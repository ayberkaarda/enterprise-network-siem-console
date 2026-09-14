package com.example.demo.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Turns a bearer access token into an authenticated security context.
 *
 * <p>A request without a usable token is passed through unchanged rather than
 * rejected here. Whether that is allowed is a question about the endpoint, not
 * about the token, and the authorisation rules already answer it: an anonymous
 * request to a protected path is turned away by the entry point with
 * {@code AUTH_REQUIRED}, while one to /auth/login proceeds.
 *
 * <p>Nothing is read from the database on this path. The role travels in the
 * signed token, which is what makes the API stateless; the cost is that a role
 * change only takes effect when the access token is next refreshed.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String token = BearerTokens.extract(request.getHeader(BearerTokens.HEADER));
        if (token == null) {
            return;
        }
        Optional<Claims> claims = jwtService.parseAccessToken(token);
        if (claims.isEmpty()) {
            return;
        }
        String username = claims.get().getSubject();
        Optional<Role> role = jwtService.roleOf(claims.get());
        if (username == null || username.isBlank() || role.isEmpty()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(role.get().authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
