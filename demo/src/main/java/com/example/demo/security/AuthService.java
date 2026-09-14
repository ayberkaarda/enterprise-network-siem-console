package com.example.demo.security;

import com.example.demo.common.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential checking and token issue.
 *
 * <p>The password comparison is done here rather than through an
 * {@code AuthenticationManager}: this service has exactly one credential type
 * to check against exactly one table, and going through the provider machinery
 * would add indirection without adding a decision.
 */
@Service
public class AuthService {

    /**
     * A real bcrypt digest of a value nobody knows, compared against whenever
     * the username does not exist. Without it an unknown username returns
     * noticeably faster than a wrong password, and the difference is enough to
     * enumerate accounts over a few thousand requests.
     */
    private static final String ABSENT_USER_DIGEST = "$2a$10$7EqJtq98hPqEX7fNZaFWoOa9YkQKXJRZQ9hXQK0aB1pCkF9O6HbGS";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String username = request == null || request.username() == null ? "" : request.username();
        String password = request == null || request.password() == null ? "" : request.password();

        Optional<User> candidate = username.isBlank() ? Optional.empty() : userRepository.findByUsername(username);

        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, ABSENT_USER_DIGEST);
            throw rejected();
        }

        User user = candidate.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            throw rejected();
        }
        return issue(user);
    }

    /**
     * Exchanges a refresh token for a new pair.
     *
     * <p>The account is reloaded rather than trusted from the token, so a
     * disabled or re-roled operator is caught at the next refresh — that window
     * is the only revocation this stateless scheme has.
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        String presented = request == null ? null : request.refreshToken();
        Claims claims = jwtService.parseRefreshToken(presented).orElseThrow(this::rejected);

        User user = userRepository
                .findByUsername(claims.getSubject())
                .filter(User::isEnabled)
                .orElseThrow(this::rejected);

        return issue(user);
    }

    private TokenResponse issue(User user) {
        return new TokenResponse(
                jwtService.generateAccessToken(user.getUsername(), user.getRole()),
                jwtService.generateRefreshToken(user.getUsername()),
                jwtService.accessTokenTtlSeconds());
    }

    private InvalidCredentialsException rejected() {
        return new InvalidCredentialsException("Kullanıcı adı veya parola hatalı.");
    }
}
