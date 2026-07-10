package ru.daniil.shifts.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthenticationRateLimitFilterTest {

    @Test
    void authEndpointIsLimitedPerIp() throws Exception {
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                mock(SecurityEventLogger.class),
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

    private MockHttpServletResponse perform(AuthenticationRateLimitFilter filter,
                                            String path,
                                            String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
