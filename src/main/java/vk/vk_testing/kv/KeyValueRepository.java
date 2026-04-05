package vk.vk_testing.kv;

import java.util.List;
import java.util.Optional;

public interface KeyValueRepository {

    void put(String key, byte[] value);

    Optional<KeyValueEntry> get(String key);

    boolean delete(String key);

    List<KeyValueEntry> range(String keyFrom, String keyTo);

    long count();
}
