package ru.daniil.shifts.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.web.ApiErrorWriter;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BearerTokenAuthenticationFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MobileAuthService mobileAuthService = mock(MobileAuthService.class);
    private final SecurityEventLogger securityEvents = mock(SecurityEventLogger.class);
    private final BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(
            mobileAuthService, securityEvents, new ApiErrorWriter(objectMapper));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicAuthenticationRoutesAreExplicitlySkipped() {
        List<String> publicPaths = List.of(
                "/api/mobile/auth/login",
                "/api/mobile/auth/refresh",
                "/api/mobile/auth/logout",
                "/api/v1/mobile/auth/login",
                "/api/v1/mobile/auth/register",
                "/api/v1/mobile/auth/registration-status",
                "/api/v1/mobile/auth/refresh",
                "/api/v1/mobile/auth/logout",
                "/api/auth/register",
                "/api/auth/registration-status"
        );

        for (String path : publicPaths) {
            assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("POST", path)), path);
        }
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/mobile/bootstrap")));
    }

    @Test
    void requestWithoutBearerHeaderPassesThroughWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/calendar");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(mobileAuthService, never()).authenticateAccessToken(org.mockito.ArgumentMatchers.any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validUserTokenCreatesUserAuthenticationAndTouchesSession() throws Exception {
        AppUser user = new AppUser("mobile-user", "{noop}x");
        when(mobileAuthService.authenticateAccessToken("access-1")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = bearerRequest("Bearer access-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("mobile-user", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertFalse(authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        verify(mobileAuthService).touchAccessToken("access-1");
        verify(chain).doFilter(request, response);
    }

    @Test
    void administratorTokenReceivesBothAuthorities() throws Exception {
        AppUser admin = new AppUser("mobile-admin", "{noop}x");
        admin.setRole("ADMIN");
        when(mobileAuthService.authenticateAccessToken("admin-token")).thenReturn(Optional.of(admin));
        MockHttpServletRequest request = bearerRequest("Bearer admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void bearerSchemeIsCaseInsensitiveAndAcceptsRepeatedWhitespace() throws Exception {
        AppUser user = new AppUser("case-user", "{noop}x");
        when(mobileAuthService.authenticateAccessToken("mixed-token")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = bearerRequest("bEaReR    mixed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals("case-user", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(mobileAuthService).touchAccessToken("mixed-token");
        assertTrue(BearerTokenAuthenticationFilter.hasBearerScheme("BEARER\tmixed-token"));
        assertEquals("mixed-token", BearerTokenAuthenticationFilter.bearerToken("BEARER\tmixed-token"));
    }

    @Test
    void malformedNonBearerAuthorizationHeaderIsLeftToTheSecurityChain() throws Exception {
        MockHttpServletRequest request = bearerRequest("Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(mobileAuthService, never()).authenticateAccessToken(org.mockito.ArgumentMatchers.any());
        assertFalse(BearerTokenAuthenticationFilter.hasBearerScheme("Bearer"));
        assertNull(BearerTokenAuthenticationFilter.bearerToken("Bearer"));
    }

    @Test
    void invalidOrBlankBearerTokenReturnsStableUnauthorizedEnvelopeAndStopsTheChain() throws Exception {
        when(mobileAuthService.authenticateAccessToken("")).thenReturn(Optional.empty());
        MockHttpServletRequest request = bearerRequest("Bearer    ");
        request.setAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE, "req-token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(response.getContentType())));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("TOKEN_INVALID", body.path("code").asText());
        assertEquals("req-token-1", body.path("requestId").asText());
        verify(chain, never()).doFilter(request, response);
        verify(mobileAuthService, never()).touchAccessToken(org.mockito.ArgumentMatchers.any());
        verify(securityEvents).warn(request, "AUTH_TOKEN_REJECTED", null, "rejected",
                "reason=invalid_or_expired_access_token");
    }

    private MockHttpServletRequest bearerRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mobile/bootstrap");
        request.addHeader("Authorization", authorization);
        return request;
    }
}
