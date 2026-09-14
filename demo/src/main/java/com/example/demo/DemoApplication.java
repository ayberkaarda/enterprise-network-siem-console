package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Fan-out work that must not sit in the caller's critical path — currently the
// push of a committed incident to the live consoles — runs on the "taskExecutor"
// bean declared in com.example.demo.config.AsyncConfig, which is backed by
// virtual threads.
@EnableAsync
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // CORS is configured in com.example.demo.config.SecurityConfig, against a
    // profile-specific allow-list read from siem.cors.allowed-origins, rather
    // than as a standalone filter here. A wildcard origin filter and Spring
    // Security's own CORS handling would otherwise both be in the chain, and
    // whichever ran first would silently decide the answer.
}
