package com.banco.co.notification.email.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class MailExecutorConfig {

    @Bean(name = "emailDispatcherExecutor")
    public ThreadPoolTaskExecutor emailDispatcherExecutor(MailProperties mailProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(mailProperties.executor().corePoolSize());
        executor.setMaxPoolSize(mailProperties.executor().maxPoolSize());
        executor.setQueueCapacity(mailProperties.executor().queueCapacity());
        executor.setThreadNamePrefix("email-dispatcher-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
