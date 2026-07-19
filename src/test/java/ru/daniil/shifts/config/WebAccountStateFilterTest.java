package ru.daniil.shifts.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebAccountStateFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void matchingAuthVersionKeepsBrowserSessionAuthenticated() throws Exception {
        UserRepository users = mock(UserRepository.class);
        AppUser current = new AppUser("alex", "hash");
        current.setAuthVersion(3L);
        when(users.findByUsername("alex")).thenReturn(Optional.of(current));
        authenticate("alex", 3L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        new WebAccountStateFilter(users).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(session.isInvalid());
    }

    @Test
    void staleAuthVersionClearsContextAndInvalidatesSession() throws Exception {
        UserRepository users = mock(UserRepository.class);
        AppUser current = new AppUser("alex", "new-hash");
        current.setAuthVersion(4L);
        when(users.findByUsername("alex")).thenReturn(Optional.of(current));
        authenticate("alex", 3L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        new WebAccountStateFilter(users).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
    }

    @Test
    void bearerAndTestPrincipalsAreNotTreatedAsCachedWebAccounts() throws Exception {
        UserRepository users = mock(UserRepository.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("alex", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        new WebAccountStateFilter(users).doFilter(
                new MockHttpServletRequest("GET", "/api/tasks"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        verify(users, never()).findByUsername("alex");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void authenticate(String username, long authVersion) {
        DutyLogUserPrincipal principal = new DutyLogUserPrincipal(
                username,
                "hash",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                authVersion);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
