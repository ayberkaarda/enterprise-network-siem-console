package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Geliştirme aşaması için devre dışı
                .cors(cors -> cors.configure(http))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws-siem/**").permitAll() // WebSocket anlık iletişimi için dışa açık
                        .requestMatchers("/api/devices/**", "/api/logs/**").permitAll() // JWT eklenene kadar API'leri açık tutuyoruz
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}