package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small single-node rate limiter for authentication entry points.
 *
 * It lives in the application so the same protection works behind stock Caddy,
 * nginx or a direct local reverse proxy. For a multi-instance deployment this
 * should be replaced by a shared Redis/gateway limiter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final String WEB_LOGIN = "/perform_login";
    private static final String REGISTRATION = "/api/auth/register";
    private static final String MOBILE_LOGIN = "/api/mobile/auth/login";

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestsSeen = new AtomicLong();
    private final SecurityEventLogger securityEvents;
    private final Clock clock;
    private final boolean enabled;
    private final int loginLimit;
    private final int loginWindowSeconds;
    private final int registrationLimit;
    private final int registrationWindowSeconds;

    public AuthenticationRateLimitFilter(
            SecurityEventLogger securityEvents,
            @Value("${dutylog.security.rate-limit.enabled:false}") boolean enabled,
            @Value("${dutylog.security.rate-limit.login-attempts:5}") int loginLimit,
            @Value("${dutylog.security.rate-limit.login-window-seconds:60}") int loginWindowSeconds,
            @Value("${dutylog.security.rate-limit.registration-attempts:5}") int registrationLimit,
            @Value("${dutylog.security.rate-limit.registration-window-seconds:3600}") int registrationWindowSeconds) {
        this(securityEvents, Clock.systemUTC(), enabled, loginLimit, loginWindowSeconds,
                registrationLimit, registrationWindowSeconds);
    }

    AuthenticationRateLimitFilter(SecurityEventLogger securityEvents,
                                  Clock clock,
                                  boolean enabled,
                                  int loginLimit,
                                  int loginWindowSeconds,
                                  int registrationLimit,
                                  int registrationWindowSeconds) {
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.enabled = enabled;
        this.loginLimit = Math.max(1, loginLimit);
        this.loginWindowSeconds = Math.max(1, loginWindowSeconds);
        this.registrationLimit = Math.max(1, registrationLimit);
        this.registrationWindowSeconds = Math.max(1, registrationWindowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !WEB_LOGIN.equals(path) && !REGISTRATION.equals(path) && !MOBILE_LOGIN.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = REGISTRATION.equals(path) ? registrationLimit : loginLimit;
        int windowSeconds = REGISTRATION.equals(path) ? registrationWindowSeconds : loginWindowSeconds;
        String ip = clientIp(request);
        Decision decision = register(path + "|" + ip, limit, windowSeconds);

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Слишком много попыток. Повтори позже\"}");
            securityEvents.warn(request, "AUTH_RATE_LIMITED", request.getParameter("username"), "rejected",
                    "endpoint=" + path + " retryAfterSeconds=" + decision.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
        occasionallyCleanup();
    }

    private Decision register(String key, int limit, int windowSeconds) {
        long now = clock.millis();
        long windowMillis = windowSeconds * 1000L;
        AtomicReference<WindowCounter> current = new AtomicReference<>();
        counters.compute(key, (ignored, old) -> {
            WindowCounter next;
            if (old == null || now - old.windowStartedAtMillis() >= windowMillis) {
                next = new WindowCounter(now, 1, windowMillis);
            } else {
                next = new WindowCounter(old.windowStartedAtMillis(), old.count() + 1, windowMillis);
            }
            current.set(next);
            return next;
        });
        WindowCounter value = current.get();
        long remainingMillis = Math.max(1L, value.windowStartedAtMillis() + value.windowMillis() - now);
        long retrySeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        return new Decision(value.count() <= limit, retrySeconds);
    }

    private void occasionallyCleanup() {
        if ((requestsSeen.incrementAndGet() & 1023L) != 0L) {
            return;
        }
        long now = clock.millis();
        counters.entrySet().removeIf(entry ->
                now - entry.getValue().windowStartedAtMillis() >= entry.getValue().windowMillis() * 2L);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp.trim();
    }

    private record WindowCounter(long windowStartedAtMillis, int count, long windowMillis) {}
    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
