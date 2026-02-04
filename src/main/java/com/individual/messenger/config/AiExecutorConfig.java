package com.individual.messenger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
