package com.mac.alert.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VirtualThreadConfig {

    @Bean(name = "alertVirtualThreadExecutor", destroyMethod = "close")
    public ExecutorService alertVirtualThreadExecutor() {
        ThreadFactory threadFactory = Thread
                .ofVirtual()
                .name("alert-worker-", 0)
                .factory();

        return Executors.newThreadPerTaskExecutor(
                threadFactory);
    }
}
