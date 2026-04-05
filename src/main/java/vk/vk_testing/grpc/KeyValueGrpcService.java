package vk.vk_testing.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vk.vk_testing.grpc.KeyValueServiceGrpc.KeyValueServiceImplBase;
import vk.vk_testing.kv.KeyValueEntry;
import vk.vk_testing.kv.KeyValueRepository;

@Service
public class KeyValueGrpcService extends KeyValueServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(KeyValueGrpcService.class);

    private final KeyValueRepository repository;

    public KeyValueGrpcService(KeyValueRepository repository) {
        this.repository = repository;
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        execute(responseObserver, () -> {
            validateKey(request.getKey());
            repository.put(request.getKey(), request.hasValue() ? request.getValue().toByteArray() : null);
            return PutResponse.newBuilder().build();
        });
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        execute(responseObserver, () -> {
            validateKey(request.getKey());
            Optional<KeyValueEntry> entry = repository.get(request.getKey());
            GetResponse.Builder builder = GetResponse.newBuilder().setFound(entry.isPresent());
            entry.map(KeyValueEntry::value)
                    .ifPresent(value -> builder.setValue(ByteString.copyFrom(value)));
            return builder.build();
        });
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        execute(responseObserver, () -> {
            validateKey(request.getKey());
            return DeleteResponse.newBuilder()
                    .setDeleted(repository.delete(request.getKey()))
                    .build();
        });
    }

    @Override
    public void range(RangeRequest request, StreamObserver<RangeEntry> responseObserver) {
        try {
            validateKey(request.getKeyFrom());
            validateKey(request.getKeyTo());
            if (request.getKeyFrom().compareTo(request.getKeyTo()) > 0) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("key_from must be less than or equal to key_to")
                        .asRuntimeException();
            }

            repository.range(request.getKeyFrom(), request.getKeyTo()).forEach(entry -> {
                RangeEntry.Builder item = RangeEntry.newBuilder().setKey(entry.key());
                if (entry.value() != null) {
                    item.setValue(ByteString.copyFrom(entry.value()));
                }
                responseObserver.onNext(item.build());
            });
            responseObserver.onCompleted();
        } catch (RuntimeException exception) {
            responseObserver.onError(toGrpcException(exception));
        } catch (Exception exception) {
            log.error("Unhandled checked exception during gRPC call", exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(messageOrDefault(exception))
                    .withCause(exception)
                    .asRuntimeException());
        }
    }

    @Override
    public void count(CountRequest request, StreamObserver<CountResponse> responseObserver) {
        execute(responseObserver, () -> CountResponse.newBuilder()
                .setCount(repository.count())
                .build());
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("key must not be blank")
                    .asRuntimeException();
        }
    }

    private static <T> void execute(StreamObserver<T> responseObserver, ResponseFactory<T> factory) {
        try {
            responseObserver.onNext(factory.create());
            responseObserver.onCompleted();
        } catch (RuntimeException exception) {
            responseObserver.onError(toGrpcException(exception));
        } catch (Exception exception) {
            log.error("Unhandled checked exception during gRPC call", exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(messageOrDefault(exception))
                    .withCause(exception)
                    .asRuntimeException());
        }
    }

    private static RuntimeException toGrpcException(RuntimeException exception) {
        if (exception instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        }
        log.error("Unhandled runtime exception during gRPC call", exception);
        return Status.INTERNAL
                .withDescription(messageOrDefault(exception))
                .withCause(exception)
                .asRuntimeException();
    }

    private static String messageOrDefault(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }

    @FunctionalInterface
    private interface ResponseFactory<T> {
        T create() throws Exception;
    }
}
