package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.daniil.shifts.web.ApiErrorWriter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Single-node auth rate limiter shared by web, legacy mobile and Android API v1. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final String WEB_LOGIN = "/perform_login";
    private static final String REGISTRATION = "/api/auth/register";
    private static final String MOBILE_LOGIN = "/api/mobile/auth/login";
    private static final String MOBILE_V1_LOGIN = "/api/v1/mobile/auth/login";
    private static final String MOBILE_V1_REGISTRATION = "/api/v1/mobile/auth/register";

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestsSeen = new AtomicLong();
    private final SecurityEventLogger securityEvents;
    private final ApiErrorWriter apiErrors;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    private final boolean enabled;
    private final int loginLimit;
    private final int loginWindowSeconds;
    private final int registrationLimit;
    private final int registrationWindowSeconds;

    @Autowired
    public AuthenticationRateLimitFilter(
            SecurityEventLogger securityEvents,
            ApiErrorWriter apiErrors,
            ClientIpResolver clientIpResolver,
            @Value("${dutylog.security.rate-limit.enabled:false}") boolean enabled,
            @Value("${dutylog.security.rate-limit.login-attempts:5}") int loginLimit,
            @Value("${dutylog.security.rate-limit.login-window-seconds:60}") int loginWindowSeconds,
            @Value("${dutylog.security.rate-limit.registration-attempts:5}") int registrationLimit,
            @Value("${dutylog.security.rate-limit.registration-window-seconds:3600}") int registrationWindowSeconds) {
        this(securityEvents, apiErrors, clientIpResolver, Clock.systemUTC(), enabled, loginLimit, loginWindowSeconds,
                registrationLimit, registrationWindowSeconds);
    }

    AuthenticationRateLimitFilter(SecurityEventLogger securityEvents,
                                  ApiErrorWriter apiErrors,
                                  Clock clock,
                                  boolean enabled,
                                  int loginLimit,
                                  int loginWindowSeconds,
                                  int registrationLimit,
                                  int registrationWindowSeconds) {
        this(securityEvents, apiErrors, new ClientIpResolver(false), clock, enabled, loginLimit,
                loginWindowSeconds, registrationLimit, registrationWindowSeconds);
    }

    AuthenticationRateLimitFilter(SecurityEventLogger securityEvents,
                                  ApiErrorWriter apiErrors,
                                  ClientIpResolver clientIpResolver,
                                  Clock clock,
                                  boolean enabled,
                                  int loginLimit,
                                  int loginWindowSeconds,
                                  int registrationLimit,
                                  int registrationWindowSeconds) {
        this.securityEvents = securityEvents;
        this.apiErrors = apiErrors;
        this.clientIpResolver = clientIpResolver;
        this.clock = clock;
        this.enabled = enabled;
        this.loginLimit = Math.max(1, loginLimit);
        this.loginWindowSeconds = Math.max(1, loginWindowSeconds);
        this.registrationLimit = Math.max(1, registrationLimit);
        this.registrationWindowSeconds = Math.max(1, registrationWindowSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !WEB_LOGIN.equals(path)
                && !REGISTRATION.equals(path)
                && !MOBILE_LOGIN.equals(path)
                && !MOBILE_V1_LOGIN.equals(path)
                && !MOBILE_V1_REGISTRATION.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean registration = REGISTRATION.equals(path) || MOBILE_V1_REGISTRATION.equals(path);
        int limit = registration ? registrationLimit : loginLimit;
        int windowSeconds = registration ? registrationWindowSeconds : loginWindowSeconds;
        String bucket = registration ? "registration" : "login";
        Decision decision = register(bucket + "|" + clientIpResolver.resolve(request), limit, windowSeconds);

        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            apiErrors.write(request, response, 429, "RATE_LIMITED",
                    "Слишком много попыток. Повтори позже");
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
            WindowCounter next = old == null || now - old.windowStartedAtMillis() >= windowMillis
                    ? new WindowCounter(now, 1, windowMillis)
                    : new WindowCounter(old.windowStartedAtMillis(), old.count() + 1, windowMillis);
            current.set(next);
            return next;
        });
        WindowCounter value = current.get();
        long remainingMillis = Math.max(1L, value.windowStartedAtMillis() + value.windowMillis() - now);
        long retrySeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        return new Decision(value.count() <= limit, retrySeconds);
    }

    private void occasionallyCleanup() {
        if ((requestsSeen.incrementAndGet() & 1023L) != 0L) return;
        long now = clock.millis();
        counters.entrySet().removeIf(entry ->
                now - entry.getValue().windowStartedAtMillis() >= entry.getValue().windowMillis() * 2L);
    }

    private record WindowCounter(long windowStartedAtMillis, int count, long windowMillis) {}
    private record Decision(boolean allowed, long retryAfterSeconds) {}
}
