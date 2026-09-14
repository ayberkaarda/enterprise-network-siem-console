package com.example.demo.config;

import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Names the executor {@code @Async} methods run on.
 *
 * <p>Spring Boot only auto-configures its own {@code applicationTaskExecutor}
 * bean when no other {@link java.util.concurrent.Executor} bean is already in
 * the context. The STOMP message broker ({@link WebSocketConfig}, enabled via
 * {@code @EnableWebSocketMessageBroker}) registers several of its own
 * ({@code clientInboundChannelExecutor}, {@code clientOutboundChannelExecutor},
 * {@code brokerChannelExecutor}, {@code messageBrokerTaskScheduler}), so that
 * auto-configuration backs off and no bean named {@code applicationTaskExecutor}
 * exists at all in this application. Boot's own async interceptor confirms
 * this at startup with a "More than one TaskExecutor bean found ... none is
 * named 'taskExecutor'" warning that lists only the broker's executors as
 * candidates.
 *
 * <p>Declaring an explicit, named executor here removes that ambiguity.
 * Building it from {@link SimpleAsyncTaskExecutorBuilder} — the same builder
 * Boot's own auto-configuration would have used — keeps {@code @Async} work on
 * virtual threads, consistent with {@code spring.threads.virtual.enabled=true}
 * in application.properties, without depending on an auto-configured bean that
 * this application never actually gets.
 */
@Configuration
public class AsyncConfig {

    @Bean
    public SimpleAsyncTaskExecutor taskExecutor(SimpleAsyncTaskExecutorBuilder builder) {
        return builder.build();
    }
}
