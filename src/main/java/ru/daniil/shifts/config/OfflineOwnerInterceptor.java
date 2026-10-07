package ru.daniil.shifts.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** Optional precondition, never an authentication source. Requests without it retain their existing API contract. */
public class OfflineOwnerInterceptor implements HandlerInterceptor {
    private final ru.daniil.shifts.service.CurrentUserService users;
    public OfflineOwnerInterceptor(ru.daniil.shifts.service.CurrentUserService users) { this.users = users; }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expected = request.getHeader("X-DutyLog-Offline-Owner");
        if (expected == null || "OPTIONS".equals(request.getMethod())) return true;
        var principal = request.getUserPrincipal();
        if (principal != null && expected.equals(users.requireUser(principal).getId().toString())) return true;
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"OFFLINE_OWNER_CHANGED\",\"error\":\"Аккаунт изменился. Перезагрузите страницу.\"}");
        return false;
    }
}
