package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.daniil.shifts.config.OfflineOwnerInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;

class OfflineOwnerInterceptorTest {
    private OfflineOwnerInterceptor guard(long id) {
        var user = mock(AppUser.class); when(user.getId()).thenReturn(id);
        var users = mock(CurrentUserService.class); when(users.requireUser(any())).thenReturn(user);
        return new OfflineOwnerInterceptor(users);
    }
    @Test void rejectsCookieAccountSwitchWithoutExecutingMutation() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/api/days/2026-10-07");
        request.addHeader("X-DutyLog-Offline-Owner", "1");
        request.setUserPrincipal(() -> "Bob");
        var response = new MockHttpServletResponse();
        assertFalse(guard(2L).preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("OFFLINE_OWNER_CHANGED"));
    }
    @Test void acceptsSameOwnerAndRetainsExistingClients() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/inbox");
        request.addHeader("X-DutyLog-Offline-Owner", "1");
        request.setUserPrincipal(() -> "Alice");
        var guard = guard(1L);
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), new Object()));
        request.removeHeader("X-DutyLog-Offline-Owner");
        assertTrue(guard.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
    @Test void rejectsRecreatedAccountEvenWhenUsernameIsUnchanged() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/api/days/2026-10-07");
        request.addHeader("X-DutyLog-Offline-Owner", "1");
        request.setUserPrincipal(() -> "Alice");
        var response = new MockHttpServletResponse();
        assertFalse(guard(2L).preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
}
