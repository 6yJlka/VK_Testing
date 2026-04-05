package vk.vk_testing.grpc;

import io.grpc.Server;
import org.springframework.context.SmartLifecycle;

public class GrpcServerLifecycle implements SmartLifecycle {

    private final Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(Server server) {
        this.server = server;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            server.start();
            running = true;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start gRPC server", exception);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        server.shutdown();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
