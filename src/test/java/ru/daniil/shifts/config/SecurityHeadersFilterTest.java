package ru.daniil.shifts.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHeadersFilterTest {
    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void baselineBrowserPolicyIsAppliedBeforeTheApplicationChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Content-Type-Options", "unsafe-old-value");
        boolean[] invoked = {false};

        filter.doFilter(request, response, (req, res) -> {
            invoked[0] = true;
            assertEquals("nosniff", ((MockHttpServletResponse) res).getHeader("X-Content-Type-Options"));
        });

        assertTrue(invoked[0]);
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("geolocation=(), microphone=(), camera=()", response.getHeader("Permissions-Policy"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("default-src 'self'"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors 'self'"));
    }

    @Test
    void directHttpsRequestsReceiveHsts() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setSecure(true);
        MockHttpServletResponse response = perform(request);

        assertEquals("max-age=31536000", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void reverseProxyHttpsIsRecognizedCaseInsensitively() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Forwarded-Proto", "HTTPS");
        MockHttpServletResponse response = perform(request);

        assertEquals("max-age=31536000", response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void plainHttpDoesNotClaimHsts() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader("X-Forwarded-Proto", "http");
        MockHttpServletResponse response = perform(request);

        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void HeadersRemainPresentWhenTheDownstreamChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/failure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (req, res) -> { throw new ServletException("boom"); }));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("form-action 'self'"));
    }

    private MockHttpServletResponse perform(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }
}
