package ru.daniil.shifts.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.web.ApiErrorWriter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthenticationRateLimitFilterTest {

    @Test
    void authEndpointIsLimitedPerIp() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                2,
                60,
                2,
                3600);

        assertEquals(200, perform(filter, "/perform_login", "203.0.113.10").getStatus());
        assertEquals(200, perform(filter, "/perform_login", "203.0.113.10").getStatus());

        MockHttpServletResponse rejected = perform(filter, "/perform_login", "203.0.113.10");
        assertEquals(429, rejected.getStatus());
        assertEquals("60", rejected.getHeader("Retry-After"));
        assertTrue(rejected.getContentAsString().contains("Слишком много попыток"));
    }

    @Test
    void unrelatedEndpointIsNotLimited() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.systemUTC(),
                true,
                1,
                60,
                1,
                3600);

        for (int i = 0; i < 5; i++) {
            assertEquals(200, perform(filter, "/api/calendar", "203.0.113.20").getStatus());
        }
    }

    @Test
    void mobileV1RegistrationUsesRegistrationBucket() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                5,
                60,
                1,
                3600);

        assertEquals(200, perform(filter, "/api/v1/mobile/auth/register", "203.0.113.30").getStatus());
        MockHttpServletResponse rejected = perform(filter, "/api/v1/mobile/auth/register", "203.0.113.30");
        assertEquals(429, rejected.getStatus());
        assertTrue(rejected.getContentAsString().contains("RATE_LIMITED"));
    }


    @Test
    void webLegacyAndV1LoginAliasesShareOneIpBucket() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                2,
                60,
                5,
                3600);

        assertEquals(200, perform(filter, "/perform_login", "203.0.113.40").getStatus());
        assertEquals(200, perform(filter, "/api/mobile/auth/login", "203.0.113.40").getStatus());
        assertEquals(429, perform(filter, "/api/v1/mobile/auth/login", "203.0.113.40").getStatus());
    }

    @Test
    void webAndMobileRegistrationAliasesShareOneIpBucket() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                5,
                60,
                1,
                3600);

        assertEquals(200, perform(filter, "/api/auth/register", "203.0.113.41").getStatus());
        assertEquals(429, perform(filter, "/api/v1/mobile/auth/register", "203.0.113.41").getStatus());
    }

    @Test
    void differentClientIpsRemainIndependentAndForwardedForUsesTheFirstHop() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                1,
                60,
                1,
                3600);

        assertEquals(200, performForwarded(filter, "/perform_login", "198.51.100.10, 10.0.0.5", null).getStatus());
        assertEquals(429, performForwarded(filter, "/api/v1/mobile/auth/login", "198.51.100.10, 10.0.0.6", null).getStatus());
        assertEquals(200, performForwarded(filter, "/perform_login", "198.51.100.11", null).getStatus());
    }

    @Test
    void realIpHeaderIsUsedWhenForwardedForIsMissing() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                true,
                1,
                60,
                1,
                3600);

        assertEquals(200, performForwarded(filter, "/perform_login", null, "198.51.100.20").getStatus());
        assertEquals(429, performForwarded(filter, "/perform_login", null, " 198.51.100.20 ").getStatus());
    }

    @Test
    void expiredWindowStartsAFreshCounterAndRetryAfterNeverDropsBelowOne() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T00:00:00Z"));
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                clock,
                true,
                1,
                2,
                1,
                2);

        assertEquals(200, perform(filter, "/perform_login", "203.0.113.50").getStatus());
        MockHttpServletResponse rejected = perform(filter, "/perform_login", "203.0.113.50");
        assertEquals(429, rejected.getStatus());
        assertTrue(Long.parseLong(rejected.getHeader("Retry-After")) >= 1L);

        clock.advanceSeconds(2);
        assertEquals(200, perform(filter, "/perform_login", "203.0.113.50").getStatus());
    }

    @Test
    void disabledLimiterAndNonPostRequestsNeverConsumeBuckets() throws Exception {
        AuthenticationRateLimitFilter disabled = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.systemUTC(),
                false,
                1,
                60,
                1,
                3600);

        for (int i = 0; i < 4; i++) {
            assertEquals(200, perform(disabled, "/perform_login", "203.0.113.60").getStatus());
        }

        AuthenticationRateLimitFilter enabled = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
                new ApiErrorWriter(new ObjectMapper()),
                Clock.systemUTC(),
                true,
                1,
                60,
                1,
                3600);
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/perform_login");
        get.setRemoteAddr("203.0.113.60");
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        enabled.doFilter(get, getResponse, new MockFilterChain());
        assertEquals(200, getResponse.getStatus());
        assertEquals(200, perform(enabled, "/perform_login", "203.0.113.60").getStatus());
    }

    private MockHttpServletResponse perform(AuthenticationRateLimitFilter filter,
                                            String path,
                                            String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse performForwarded(AuthenticationRateLimitFilter filter,
                                                     String path,
                                                     String forwardedFor,
                                                     String realIp) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("10.0.0.2");
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        if (realIp != null) request.addHeader("X-Real-IP", realIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
