package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the two token kinds the console uses.
 *
 * <p>Both are HMAC-SHA256 JWTs signed with the same secret, which is supplied
 * from the environment and has no in-code default: a signing key that ships
 * with the source is not a secret, so the application refuses to start rather
 * than fall back to a well known value.
 *
 * <p>Access and refresh tokens are told apart by a {@code typ} claim, checked on
 * every parse. Without it a stolen access token could be replayed against the
 * refresh endpoint to mint fresh credentials indefinitely, which would defeat
 * the point of giving the access token a short life.
 *
 * <p>Refresh tokens are stateless — nothing about them is stored server side.
 * Refreshing therefore issues a new refresh token but cannot revoke the one
 * that was presented; the presented token stays usable until it expires on its
 * own. That is a deliberate trade of revocation for having no session store,
 * and it is why the refresh lifetime is measured in days, not months.
 */
@Service
public class JwtService {

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TOKEN_TYPE = "typ";
    static final String TOKEN_TYPE_ACCESS = "access";
    static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    // Explicitly marked rather than relying on Spring's single-constructor
    // inference: this class has a second, package-private constructor for
    // tests to inject a fixed clock, so there is more than one candidate and
    // Spring cannot pick one on its own.
    @Autowired
    public JwtService(
            @Value("${siem.jwt.secret}") String secret,
            @Value("${siem.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl,
            @Value("${siem.jwt.refresh-token-ttl:P7D}") Duration refreshTokenTtl) {
        this(secret, accessTokenTtl, refreshTokenTtl, Clock.systemUTC());
    }

    JwtService(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, Clock clock) {
        // Rejects a secret that is too short for HS256 rather than padding it,
        // so a weak key is a startup failure instead of a silent weakness.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    /** Access token lifetime in seconds, as reported to the client. */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public String generateAccessToken(String username, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh tokens deliberately carry no role claim: the role is read back
     * from the database when the token is exchanged, so a demotion takes effect
     * at the next refresh instead of being frozen into the credential.
     */
    public String generateRefreshToken(String username) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Verified claims of an access token, or empty if it is not one. */
    public Optional<Claims> parseAccessToken(String token) {
        return parse(token, TOKEN_TYPE_ACCESS);
    }

    /** Verified claims of a refresh token, or empty if it is not one. */
    public Optional<Claims> parseRefreshToken(String token) {
        return parse(token, TOKEN_TYPE_REFRESH);
    }

    /**
     * Reads the role out of verified claims. An unknown or missing value is
     * reported as empty rather than guessed at, so a token minted by an older
     * or tampered-with issuer cannot be granted a default role.
     */
    public Optional<Role> roleOf(Claims claims) {
        String raw = claims.get(CLAIM_ROLE, String.class);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Role.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Claims> parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // A bad signature, an expired token and a malformed string are all
            // the same answer to the caller: this is not a credential. Telling
            // them which it was only helps an attacker narrow the search.
            return Optional.empty();
        }
    }
}
