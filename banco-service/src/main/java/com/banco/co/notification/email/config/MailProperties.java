package com.banco.co.notification.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "banco.mail")
public record MailProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String from,
        Relay relay,
        Executor executor
) {
    public record Relay(
            int batchSize,
            long pollDelayMs,
            int maxAttempts
    ) {
    }

    public record Executor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {
    }
}
