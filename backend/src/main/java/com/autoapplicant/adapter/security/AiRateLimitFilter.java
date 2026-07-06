package com.autoapplicant.adapter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple per-user token-bucket rate limiter for AI endpoints.
 * Resets every {@code windowSeconds} seconds (default 60).
 * Each user gets {@code maxRequests} AI calls per window (default 20).
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

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
        return !request.getRequestURI().startsWith("/api/v1/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate-limit mutating AI calls (POST), not GETs
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

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
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many AI requests. Please wait before trying again.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private record UserBucket(long windowStart, AtomicInteger counter) {}
}
