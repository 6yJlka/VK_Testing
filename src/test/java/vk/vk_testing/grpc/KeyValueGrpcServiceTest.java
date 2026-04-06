package vk.vk_testing.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vk.vk_testing.kv.KeyValueEntry;
import vk.vk_testing.kv.KeyValueRepository;

@ExtendWith(MockitoExtension.class)
class KeyValueGrpcServiceTest {

    @Mock
    private KeyValueRepository repository;

    @InjectMocks
    private KeyValueGrpcService service;

    @Test
    void putPassesProvidedValueToRepository() {
        TestObserver<PutResponse> observer = new TestObserver<>();
        byte[] value = "hello".getBytes();

        service.put(PutRequest.newBuilder()
                .setKey("alpha")
                .setValue(ByteString.copyFrom(value))
                .build(), observer);

        verify(repository).put("alpha", value);
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
    }

    @Test
    void getReturnsFoundEntryWithNullValue() {
        when(repository.get("alpha")).thenReturn(Optional.of(new KeyValueEntry("alpha", null)));

        TestObserver<GetResponse> observer = new TestObserver<>();
        service.get(GetRequest.newBuilder().setKey("alpha").build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        GetResponse response = observer.values.getFirst();
        assertThat(response.getFound()).isTrue();
        assertThat(response.hasValue()).isFalse();
    }

    @Test
    void getReturnsFoundEntryWithValue() {
        when(repository.get("alpha")).thenReturn(Optional.of(new KeyValueEntry("alpha", "hello".getBytes())));

        TestObserver<GetResponse> observer = new TestObserver<>();
        service.get(GetRequest.newBuilder().setKey("alpha").build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        GetResponse response = observer.values.getFirst();
        assertThat(response.getFound()).isTrue();
        assertThat(response.getValue()).isEqualTo(ByteString.copyFromUtf8("hello"));
    }

    @Test
    void putPassesNullWhenValueIsMissing() {
        TestObserver<PutResponse> observer = new TestObserver<>();

        service.put(PutRequest.newBuilder().setKey("alpha").build(), observer);

        verify(repository).put("alpha", null);
        assertThat(observer.completed).isTrue();
        assertThat(observer.error).isNull();
    }

    @Test
    void deleteReturnsDeletedFlagFromRepository() {
        when(repository.delete("alpha")).thenReturn(true);

        TestObserver<DeleteResponse> observer = new TestObserver<>();
        service.delete(DeleteRequest.newBuilder().setKey("alpha").build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.getFirst().getDeleted()).isTrue();
    }

    @Test
    void countReturnsRepositoryCount() {
        when(repository.count()).thenReturn(42L);

        TestObserver<CountResponse> observer = new TestObserver<>();
        service.count(CountRequest.newBuilder().build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.getFirst().getCount()).isEqualTo(42L);
    }

    @Test
    void rangeRejectsInvalidBounds() {
        TestObserver<RangeEntry> observer = new TestObserver<>();

        service.range(RangeRequest.newBuilder()
                .setKeyFrom("z")
                .setKeyTo("a")
                .build(), observer);

        assertThat(observer.values).isEmpty();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException exception = (StatusRuntimeException) observer.error;
        assertThat(exception.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    void rangeStreamsEntriesOneByOne() {
        when(repository.range("a", "c")).thenReturn(List.of(
                new KeyValueEntry("a", "one".getBytes()),
                new KeyValueEntry("b", null)
        ));

        TestObserver<RangeEntry> observer = new TestObserver<>();
        service.range(RangeRequest.newBuilder()
                .setKeyFrom("a")
                .setKeyTo("c")
                .build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(2);
        assertThat(observer.values.get(0).getKey()).isEqualTo("a");
        assertThat(observer.values.get(0).getValue()).isEqualTo(ByteString.copyFromUtf8("one"));
        assertThat(observer.values.get(1).getKey()).isEqualTo("b");
        assertThat(observer.values.get(1).hasValue()).isFalse();
    }

    private static final class TestObserver<T> implements StreamObserver<T> {

        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
