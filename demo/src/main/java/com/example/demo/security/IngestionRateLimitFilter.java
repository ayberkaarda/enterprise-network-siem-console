package com.example.demo.security;

import com.example.demo.common.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client token bucket in front of the event ingestion endpoint.
 *
 * <p>Ingestion is the one endpoint external producers are pointed at, so it is
 * also the one that can be pushed hardest — a misconfigured collector in a
 * retry loop writes a row and runs a correlation pass per request, and will
 * fill the evidence table faster than an analyst can look at it. The bucket
 * bounds that without needing the producer to behave.
 *
 * <p>Buckets are held in memory and keyed by remote address, which makes the
 * limit per instance rather than per deployment. That is the honest scope of an
 * in-process counter; a shared limit would need shared state, and this project
 * deliberately keeps its state in PostgreSQL alone.
 *
 * <p>Only writes are limited. Reading the stored stream back is a console
 * operation and is bounded by paging instead.
 */
public class IngestionRateLimitFilter extends OncePerRequestFilter {

    private final String protectedPath;
    private final long capacity;
    private final Duration refillPeriod;
    private final Cache<String, Bucket> buckets;
    private final ProblemResponseWriter problemResponseWriter;

    public IngestionRateLimitFilter(
            String protectedPath,
            long capacity,
            Duration refillPeriod,
            Duration idleRetention,
            ProblemResponseWriter problemResponseWriter) {
        this.protectedPath = protectedPath;
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
        this.problemResponseWriter = problemResponseWriter;
        // Bounded and self-evicting: without both, a spray of forged source
        // addresses would turn the limiter itself into the memory leak it is
        // meant to prevent.
        this.buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(idleRetention)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !protectedPath.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(clientKey(request), key -> newBucket());
        if (bucket != null && bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        problemResponseWriter.write(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded",
                "Bu kaynak icin izin verilen istek hizi asildi.",
                ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod)
                        .build())
                .build();
    }

    /**
     * The transport peer, never a forwarded-for header: this service is reached
     * directly on the compose network, so trusting a client supplied header
     * would let any producer pick its own bucket and opt out of the limit.
     */
    private String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
