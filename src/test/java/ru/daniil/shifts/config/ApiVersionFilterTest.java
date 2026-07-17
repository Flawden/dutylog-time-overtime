package ru.daniil.shifts.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiVersionFilterTest {
    private final ApiVersionFilter filter = new ApiVersionFilter();

    @Test
    void v1RoutesAdvertiseTheStableContract() throws Exception {
        MockHttpServletResponse response = perform("/api/v1/tasks");

        assertEquals("v1", response.getHeader("X-DutyLog-Api-Version"));
        assertNull(response.getHeader("Deprecation"));
        assertNull(response.getHeader("Link"));
    }

    @Test
    void legacyMobileRoutesAdvertiseTheirSuccessor() throws Exception {
        MockHttpServletResponse response = perform("/api/mobile/sync");

        assertEquals("true", response.getHeader("Deprecation"));
        assertEquals("</api/v1/mobile>; rel=\"successor-version\"", response.getHeader("Link"));
        assertNull(response.getHeader("X-DutyLog-Api-Version"));
    }

    @Test
    void regularWebApiRoutesDoNotPretendToBeVersionedOrDeprecated() throws Exception {
        MockHttpServletResponse response = perform("/api/calendar");

        assertNull(response.getHeader("X-DutyLog-Api-Version"));
        assertNull(response.getHeader("Deprecation"));
        assertNull(response.getHeader("Link"));
    }

    @Test
    void similarlyNamedNonApiPathsDoNotReceiveContractHeaders() throws Exception {
        MockHttpServletResponse response = perform("/api/v10/tasks");

        assertNull(response.getHeader("X-DutyLog-Api-Version"));
        assertNull(response.getHeader("Deprecation"));
    }

    private MockHttpServletResponse perform(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
