package com.example.demo.config;

import com.example.demo.security.StompAuthChannelInterceptor;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The live push channel: a SockJS-backed STOMP endpoint and an in-memory broker.
 *
 * <p>Two separate gates protect it, because the socket has two separate doors.
 * The handshake is an ordinary HTTP request, and a page on any origin can issue
 * one, so the endpoint accepts it only from the same origins the REST API is
 * configured for. The session itself is opened by a STOMP CONNECT frame, which
 * is where the bearer token travels — the browser cannot put a header on the
 * handshake — so that frame is verified by an interceptor on the inbound
 * channel before a session exists.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final String[] allowedOrigins;

    /**
     * Reads the origin list from the same {@code siem.cors.allowed-origins}
     * property the REST CORS configuration uses. Keeping one list avoids the
     * failure mode where the API accepts a deployment's origin and the socket
     * silently does not, and it means a new environment is described in one
     * place instead of two.
     */
    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            @Value("${siem.cors.allowed-origins}") String allowedOrigins) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Exact origins rather than patterns: every entry in the property is a
        // literal origin, and a pattern list is only worth its looser matching
        // when something in it actually needs a wildcard.
        registry.addEndpoint("/ws-siem").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    /**
     * Puts the CONNECT-frame credential check in front of the broker. This is
     * the only authentication the STOMP layer gets: the HTTP filter chain
     * deliberately leaves {@code /ws-siem/**} open so the handshake can happen
     * at all.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
