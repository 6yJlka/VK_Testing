package vk.vk_testing.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import io.tarantool.client.TarantoolClient;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TarantoolKeyValueRepository implements KeyValueRepository {

    private static final String INIT_SCHEMA = """
            local space = box.space.KV
            if space == nil then
                space = box.schema.space.create('KV', { if_not_exists = true })
            end
            space:format({
                { name = 'key', type = 'string' },
                { name = 'value', type = 'varbinary', is_nullable = true }
            })
            space:create_index('primary', {
                if_not_exists = true,
                parts = {
                    { field = 'key', type = 'string' }
                }
            })
            return true
            """;

    private static final String PUT_SCRIPT = """
            local key, value = ...
            box.space.KV:replace({ key, value })
            return true
            """;

    private static final String GET_SCRIPT = """
            local key = ...
            local tuple = box.space.KV:get(key)
            if tuple == nil then
                return nil
            end
            return { tuple[1], tuple[2] }
            """;

    private static final String DELETE_SCRIPT = """
            local key = ...
            local tuple = box.space.KV:delete(key)
            return tuple ~= nil
            """;

    private static final String RANGE_SCRIPT = """
            local from_key, to_key = ...
            local result = {}
            for _, tuple in box.space.KV.index.primary:pairs(from_key, { iterator = 'GE' }) do
                if tuple[1] > to_key then
                    break
                end
                table.insert(result, { tuple[1], tuple[2] })
            end
            return result
            """;

    private static final String COUNT_SCRIPT = """
            return box.space.KV:count()
            """;

    private final TarantoolClient client;

    public TarantoolKeyValueRepository(TarantoolClient client) {
        this.client = client;
        evaluateSingle(INIT_SCHEMA, List.of(), Boolean.class);
    }

    @Override
    public void put(String key, byte[] value) {
        evaluateSingle(PUT_SCRIPT, Arrays.asList(key, value), Boolean.class);
    }

    @Override
    public Optional<KeyValueEntry> get(String key) {
        List<Object> raw = evaluateNullable(GET_SCRIPT, List.of(key), new TypeReference<>() {});
        List<Object> tuple = unwrapNullableTuple(raw);
        if (tuple == null) {
            return Optional.empty();
        }
        return Optional.of(toEntry(tuple));
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(evaluateSingle(DELETE_SCRIPT, List.of(key), Boolean.class));
    }

    @Override
    public List<KeyValueEntry> range(String keyFrom, String keyTo) {
        List<List<Object>> rows = evaluateSingle(RANGE_SCRIPT, List.of(keyFrom, keyTo), new TypeReference<>() {});
        return unwrapRows(rows).stream().map(TarantoolKeyValueRepository::toEntry).toList();
    }

    @Override
    public long count() {
        Number number = evaluateSingle(COUNT_SCRIPT, List.of(), Number.class);
        return number.longValue();
    }

    private <T> T evaluateSingle(String script, List<?> arguments, Class<T> type) {
        return first(client.eval(script, arguments, type).join().get());
    }

    private <T> T evaluateSingle(String script, List<?> arguments, TypeReference<T> typeReference) {
        return client.eval(script, arguments, typeReference).join().get();
    }

    private <T> T evaluateNullable(String script, List<?> arguments, TypeReference<T> typeReference) {
        return client.eval(script, arguments, typeReference).join().get();
    }

    private static <T> T first(List<T> result) {
        Objects.requireNonNull(result, "Tarantool returned null response");
        if (result.isEmpty()) {
            throw new IllegalStateException("Tarantool returned empty response");
        }
        return result.getFirst();
    }

    private static KeyValueEntry toEntry(List<Object> tuple) {
        if (tuple == null || tuple.isEmpty()) {
            throw new IllegalStateException("Tarantool returned empty tuple");
        }
        String key = (String) tuple.get(0);
        byte[] value = tuple.size() > 1 ? (byte[]) tuple.get(1) : null;
        return new KeyValueEntry(key, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> unwrapTuple(List<Object> tuple) {
        if (tuple.size() == 1 && tuple.getFirst() instanceof List<?> nested) {
            return (List<Object>) nested;
        }
        return tuple;
    }

    private static List<Object> unwrapNullableTuple(List<Object> tuple) {
        if (tuple == null || tuple.isEmpty()) {
            return null;
        }
        List<Object> unwrapped = unwrapTuple(tuple);
        if (unwrapped == null || unwrapped.isEmpty()) {
            return null;
        }
        if (unwrapped.size() == 1 && unwrapped.getFirst() == null) {
            return null;
        }
        return unwrapped;
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> unwrapRows(List<List<Object>> rows) {
        if (rows.size() == 1 && rows.getFirst() instanceof List<?> nestedRows) {
            if (nestedRows.isEmpty() || nestedRows.getFirst() instanceof List<?>) {
                return (List<List<Object>>) (List<?>) nestedRows;
            }
        }
        return rows;
    }
}
