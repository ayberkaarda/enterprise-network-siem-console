package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // İstemcilerin (Angular) abone olacağı kanalın prefix'i
        config.enableSimpleBroker("/topic");
        // İstemcilerden gelecek mesajların prefix'i
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Angular'ın bağlanacağı uç nokta (endpoint).
        // Geliştirme aşamasında CORS'a takılmamak için "*" verdik.
        registry.addEndpoint("/ws-siem").setAllowedOriginPatterns("*").withSockJS();
    }
}