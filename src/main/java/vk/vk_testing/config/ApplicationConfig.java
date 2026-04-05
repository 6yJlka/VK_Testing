package vk.vk_testing.config;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.tarantool.client.box.TarantoolBoxClient;
import io.tarantool.client.factory.TarantoolFactory;
import io.tarantool.client.factory.TarantoolBoxClientBuilder;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vk.vk_testing.grpc.GrpcServerLifecycle;

@Configuration
@EnableConfigurationProperties({GrpcServerProperties.class, TarantoolProperties.class})
public class ApplicationConfig {

    @Bean(destroyMethod = "close")
    TarantoolBoxClient tarantoolClient(TarantoolProperties properties) throws Exception {
        TarantoolBoxClientBuilder builder = TarantoolFactory.box()
                .withHost(properties.host())
                .withPort(properties.port())
                .withUser(properties.username())
                .withConnectTimeout(properties.connectTimeout().toMillis());
        if (properties.password() != null && !properties.password().isBlank()) {
            builder.withPassword(properties.password());
        }
        return builder.build();
    }

    @Bean
    Server grpcServer(List<BindableService> services, GrpcServerProperties properties) {
        ServerBuilder<?> builder = ServerBuilder.forPort(properties.port());
        services.forEach(builder::addService);
        return builder.build();
    }

    @Bean
    GrpcServerLifecycle grpcServerLifecycle(Server server) {
        return new GrpcServerLifecycle(server);
    }
}
