package com.interview.platform.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.platform.api.ApiResponse;
import com.interview.platform.api.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final List<RateLimitRule> rules;

    public RateLimitingFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.auth.max-requests:10}") int authLimit,
            @Value("${app.rate-limit.auth.window-seconds:60}") long authWindowSeconds,
            @Value("${app.rate-limit.otp.max-requests:5}") int otpLimit,
            @Value("${app.rate-limit.otp.window-seconds:300}") long otpWindowSeconds,
            @Value("${app.rate-limit.review.max-requests:12}") int reviewLimit,
            @Value("${app.rate-limit.review.window-seconds:300}") long reviewWindowSeconds,
            @Value("${app.rate-limit.booking.max-requests:12}") int bookingLimit,
            @Value("${app.rate-limit.booking.window-seconds:300}") long bookingWindowSeconds
    ) {
        this.objectMapper = objectMapper;
        this.rules = List.of(
                new RateLimitRule("otp", List.of(
                        "/api/auth/send-otp",
                        "/api/auth/resend-otp",
                        "/api/auth/verify-otp",
                        "/api/auth/forgot-password",
                        "/api/auth/verify-reset-otp",
                        "/api/auth/reset-password",
                        "/api/users/me/resend-otp",
                        "/api/users/me/verify-otp"
                ), otpLimit, otpWindowSeconds),
                new RateLimitRule("auth", List.of(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout"
                ), authLimit, authWindowSeconds),
                new RateLimitRule("review", List.of(
                        "/api/feedback",
                        "/api/reports",
                        "/api/trust/reports",
                        "/api/trust/verification-request"
                ), reviewLimit, reviewWindowSeconds),
                new RateLimitRule("booking", List.of(
                        "/api/bookings",
                        "/api/sessions"
                ), bookingLimit, bookingWindowSeconds)
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return rules.stream().noneMatch(rule -> rule.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitRule rule = rules.stream()
                .filter(candidate -> candidate.matches(request))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        cleanupExpired();
        Instant now = Instant.now();
        String key = rule.name() + ":" + identityKey(request);
        WindowCounter counter = counters.compute(key, (ignored, existing) -> refresh(existing, now, rule.windowSeconds()));
        int currentCount = counter.count().incrementAndGet();
        if (currentCount > rule.maxRequests()) {
            long retryAfter = Math.max(1, counter.windowStart().plusSeconds(rule.windowSeconds()).getEpochSecond() - now.getEpochSecond());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> body = ApiResponse.error(
                    "Too many requests. Please wait and try again.",
                    "RATE_LIMITED",
                    Map.of("retryAfterSeconds", retryAfter, "limitGroup", rule.name())
            );
            objectMapper.writeValue(response.getWriter(), body);
            log.warn("rate_limit_exceeded requestId={} rule={} key={} retryAfterSeconds={}",
                    RequestContext.requestId(), rule.name(), key, retryAfter);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public Map<String, Object> diagnostics() {
        return Map.of(
                "trackedKeys", counters.size(),
                "rules", rules.stream().map(rule -> Map.of(
                        "name", rule.name(),
                        "maxRequests", rule.maxRequests(),
                        "windowSeconds", rule.windowSeconds()
                )).toList()
        );
    }

    private WindowCounter refresh(WindowCounter existing, Instant now, long windowSeconds) {
        if (existing == null || existing.windowStart().plusSeconds(windowSeconds).isBefore(now)) {
            return new WindowCounter(now, new AtomicInteger(0));
        }
        return existing;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            WindowCounter counter = entry.getValue();
            RateLimitRule rule = rules.stream().filter(candidate -> key.startsWith(candidate.name() + ":")).findFirst().orElse(null);
            return rule != null && counter.windowStart().plusSeconds(rule.windowSeconds()).isBefore(now);
        });
    }

    private String identityKey(HttpServletRequest request) {
        String userId = authenticatedUserId();
        if (userId != null) {
            return userId;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return Objects.toString(request.getRemoteAddr(), "unknown");
    }

    private String authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.interview.platform.security.UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        return null;
    }

    private record RateLimitRule(String name, List<String> pathPrefixes, int maxRequests, long windowSeconds) {
        private boolean matches(HttpServletRequest request) {
            String path = request.getRequestURI();
            if (path == null) {
                return false;
            }
            if ("booking".equals(name) && "GET".equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            if ("review".equals(name) && "GET".equalsIgnoreCase(request.getMethod())) {
                return false;
            }
            return pathPrefixes.stream().anyMatch(path::startsWith);
        }
    }

    private record WindowCounter(Instant windowStart, AtomicInteger count) {
    }
}
