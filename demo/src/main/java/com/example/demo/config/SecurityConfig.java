package com.example.demo.config;

import com.example.demo.security.IngestionRateLimitFilter;
import com.example.demo.security.JwtAuthenticationFilter;
import com.example.demo.security.ProblemResponseWriter;
import com.example.demo.security.RestAccessDeniedHandler;
import com.example.demo.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Wires the pieces already living in {@code security} into an actual request
 * pipeline.
 *
 * <p>Everything is stateless: there is no form login and no HTTP session, only
 * the bearer token {@link JwtAuthenticationFilter} reads on every request.
 * CSRF protection exists to defend session cookies
 * against being ridden by another site; there is no session cookie here, so it
 * is switched off rather than kept as dead weight.
 *
 * <p>{@code /ws-siem/**} is left open at this layer on purpose. SockJS reaches
 * it through plain XHR/polling requests before a STOMP session even exists —
 * the browser's WebSocket API has no way to attach an Authorization header to
 * them — and the console's STOMP client instead carries the bearer token as a
 * CONNECT frame header once the session is up (see {@code RealtimeService} in
 * the frontend). Requiring an HTTP-layer credential here would not add a check
 * at the STOMP layer; it would simply stop the socket from ever reaching one,
 * breaking every live update. Enforcing the CONNECT-frame credential is a STOMP
 * channel interceptor, not an HTTP filter, and is not wired up yet — the socket
 * is reachable without a token until that lands.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Per-client token bucket in front of {@code POST /api/v1/events}.
     *
     * <p>Placed ahead of authentication in the chain: an external collector
     * being throttled is not an authentication failure, and there is no reason
     * to pay for token verification on a request that is about to be rejected
     * anyway.
     */
    @Bean
    public IngestionRateLimitFilter ingestionRateLimitFilter(
            ProblemResponseWriter problemResponseWriter,
            @Value("${siem.ratelimit.events.path:/api/v1/events}") String protectedPath,
            @Value("${siem.ratelimit.events.capacity:20}") long capacity,
            @Value("${siem.ratelimit.events.refill-period:PT1S}") Duration refillPeriod,
            @Value("${siem.ratelimit.events.idle-retention:PT10M}") Duration idleRetention) {
        return new IngestionRateLimitFilter(
                protectedPath, capacity, refillPeriod, idleRetention, problemResponseWriter);
    }

    /**
     * Origins allowed to call this API with credentials, read from a property
     * rather than hard-coded: the local development list and the docker-compose
     * deployment's actual origin are different, and a wildcard would defeat the
     * point of listing either.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${siem.cors.allowed-origins}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            IngestionRateLimitFilter ingestionRateLimitFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Scraped by Prometheus over the private docker-compose network only
                        // (siem-network), never exposed to a public interface — see docker-compose.yml.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // See the class-level note: the HTTP handshake has to stay open for
                        // the socket to be reachable at all, credential enforcement for it
                        // happens (or, right now, does not yet happen) at the STOMP layer.
                        .requestMatchers("/ws-siem/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // Order matters here beyond readability: addFilterBefore resolves its
                // "before" argument against filters already registered in this chain,
                // so jwtAuthenticationFilter's position has to be established first —
                // referencing its class before that call would fail with "does not
                // have a registered order".
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(ingestionRateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
