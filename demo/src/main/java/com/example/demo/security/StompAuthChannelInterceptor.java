package com.example.demo.security;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Optional;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP session at the moment it is opened.
 *
 * <p>The HTTP handshake that carries a WebSocket into existence cannot be
 * guarded the way a REST call is: the browser's WebSocket API offers no way to
 * put an {@code Authorization} header on it, so {@code /ws-siem/**} is left
 * reachable by the HTTP filter chain. The credential arrives one layer up
 * instead, as a STOMP header on the CONNECT frame, and this interceptor is the
 * gate that reads it. Without it the handshake being open would mean the live
 * feeds were open too — device inventory, incidents and console-wide metrics
 * are all pushed over them.
 *
 * <p>The verification chain is deliberately the same one
 * {@link JwtAuthenticationFilter} applies to REST calls, down to sharing
 * {@link BearerTokens} for the header parsing, so a token that opens a socket
 * and a token that opens an endpoint mean exactly the same thing.
 *
 * <p>Where the two differ is what happens on failure. The filter lets an
 * unauthenticated request continue and leaves the verdict to the authorisation
 * rules, because some HTTP endpoints are public. No STOMP destination is: every
 * topic carries operational security data. So a CONNECT without a usable token
 * is refused outright by throwing here, which Spring turns into an ERROR frame,
 * and no session is ever established — there is no anonymous socket to
 * subscribe with.
 *
 * <p>Only the connect frames are inspected. A subscription cannot exist without
 * a session, and a session cannot exist without having passed through here, so
 * re-checking every SUBSCRIBE would re-answer a question already settled. Both
 * spellings of the connect frame are covered: STOMP 1.2 permits {@code STOMP}
 * in place of {@code CONNECT}, and a client using it would otherwise walk in
 * unchecked.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /**
     * Returned to the client in the ERROR frame. Intentionally says only that a
     * credential was required, never which part of the presented one failed:
     * absent, expired, wrongly signed and "a refresh token, not an access one"
     * are the same answer to the caller, and distinguishing them only helps
     * someone probing.
     */
    private static final String REJECTION_MESSAGE = "A valid bearer access token is required to open a session";

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !isConnect(accessor.getCommand())) {
            return message;
        }
        // Inbound STOMP messages are built with a mutable accessor precisely so
        // that an interceptor can attach the principal here; the session keeps
        // it for its whole lifetime.
        accessor.setUser(authenticate(accessor));
        return message;
    }

    private static boolean isConnect(StompCommand command) {
        return StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command);
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String token = BearerTokens.extract(accessor.getFirstNativeHeader(BearerTokens.HEADER));
        if (token == null) {
            throw new AuthenticationCredentialsNotFoundException(REJECTION_MESSAGE);
        }
        Optional<Claims> claims = jwtService.parseAccessToken(token);
        if (claims.isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException(REJECTION_MESSAGE);
        }
        String username = claims.get().getSubject();
        Optional<Role> role = jwtService.roleOf(claims.get());
        if (username == null || username.isBlank() || role.isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException(REJECTION_MESSAGE);
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(role.get().authority())));
    }
}
