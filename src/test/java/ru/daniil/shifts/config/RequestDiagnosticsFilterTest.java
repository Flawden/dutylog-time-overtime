package ru.daniil.shifts.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDiagnosticsFilterTest {
    private final RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter();

    @Test
    void validCallerRequestIdIsTrimmedEchoedAndExposedToDownstreamCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar");
        request.addHeader("X-Request-Id", "  client_42.trace-7  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Object> downstreamAttribute = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                downstreamAttribute.set(req.getAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE)));

        assertEquals("client_42.trace-7", response.getHeader("X-Request-Id"));
        assertEquals("client_42.trace-7", downstreamAttribute.get());
    }

    @Test
    void invalidCallerRequestIdIsRejectedAndReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar");
        request.addHeader("X-Request-Id", "bad id with spaces\r\nInjected: yes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String generated = response.getHeader("X-Request-Id");
        assertNotNull(generated);
        assertTrue(generated.matches("[A-Za-z0-9._-]{8}"));
        assertNotEquals("bad id with spaces\r\nInjected: yes", generated);
    }

    @Test
    void oversizedRequestIdIsReplacedInsteadOfReflected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar");
        request.addHeader("X-Request-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(8, response.getHeader("X-Request-Id").length());
    }

    @Test
    void missingIdsGenerateIndependentCorrelationValues() throws Exception {
        String first = performWithoutId().getHeader("X-Request-Id");
        String second = performWithoutId().getHeader("X-Request-Id");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void downstreamStatusIsPreserved() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(418));

        assertEquals(418, response.getStatus());
        assertNotNull(response.getHeader("X-Request-Id"));
    }

    @Test
    void correlationIdStillExistsWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/failure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (req, res) -> { throw new ServletException("boom"); }));
        assertNotNull(response.getHeader("X-Request-Id"));
        assertEquals(response.getHeader("X-Request-Id"),
                request.getAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE));
    }

    private MockHttpServletResponse performWithoutId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response;
    }
}
