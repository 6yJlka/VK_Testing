package vk.vk_testing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.server")
public record GrpcServerProperties(int port) {
}
