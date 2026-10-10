package com.autoapplicant.adapter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple per-user token-bucket rate limiter for AI endpoints.
 * Resets every {@code windowSeconds} seconds (default 60).
 * Each user gets {@code maxRequests} AI calls per window (default 20).
 * Which requests count is decided by {@link AiRateLimitedEndpoints}.
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_BODY =
            "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many AI requests. Please wait before trying again.\"}";

    private final int maxRequests;
    private final long windowMillis;

    private final ConcurrentHashMap<UUID, UserBucket> buckets = new ConcurrentHashMap<>();

    public AiRateLimitFilter(
            @Value("${app.rate-limit.ai.max-requests:20}") int maxRequests,
            @Value("${app.rate-limit.ai.window-seconds:60}") int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AiRateLimitedEndpoints.matches(request.getMethod(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserBucket bucket = buckets.compute(userId, (id, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart > windowMillis) {
                return new UserBucket(now, new AtomicInteger(0));
            }
            return existing;
        });

        int count = bucket.counter.incrementAndGet();
        if (count > maxRequests) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(RATE_LIMITED_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private record UserBucket(long windowStart, AtomicInteger counter) {}
}
