package com.bettips.backend.config;


import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    // One bucket per IP address
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createBucket(String key) {
        // Auth: 5 attempts/min — brute force protection
        if (key.startsWith("AUTH:")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1))
                            .build())
                    .build();
        }
        // Payment: 3/min — no one initiates 3+ STK pushes in a minute legitimately
        if (key.startsWith("PAY:")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(5)
                            .refillGreedy(5, Duration.ofMinutes(1))
                            .build())
                    .build();
        }
        // General: 20/min — enough for normal app usage
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(20)
                        .refillGreedy(20, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ip = getClientIP(httpRequest);
        String uri = httpRequest.getRequestURI();

        // Use a stricter bucket key for auth endpoints
        String bucketKey = (uri.startsWith("/api/auth")) ? "AUTH:" + ip : ip;

        Bucket bucket = buckets.computeIfAbsent(bucketKey, this::createBucket);

        if (bucket.tryConsume(1)) {
            // Add headers so clients know their limit status
            httpResponse.addHeader("X-RateLimit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\": \"Too many requests. Please slow down.\", \"retryAfter\": \"60s\"}"
            );
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
