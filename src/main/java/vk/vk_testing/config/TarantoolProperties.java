package vk.vk_testing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tarantool")
public record TarantoolProperties(
        String host,
        int port,
        String username,
        String password,
        Duration connectTimeout,
        Duration requestTimeout
) {
}
