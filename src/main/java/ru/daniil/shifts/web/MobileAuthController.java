package ru.daniil.shifts.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.dto.Dtos.MobileLoginRequest;
import ru.daniil.shifts.dto.Dtos.MobileLogoutRequest;
import ru.daniil.shifts.dto.Dtos.MobileRefreshRequest;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.dto.Dtos.MobileUserDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.MobileAuthService;

import java.security.Principal;
import java.util.List;

/**
 * Авторизация для Android-клиента: access/refresh tokens.
 * Обычная веб-авторизация через форму и JSESSIONID остаётся без изменений.
 */
@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {
    private final MobileAuthService mobileAuthService;
    private final CurrentUserService currentUserService;

    public MobileAuthController(MobileAuthService mobileAuthService, CurrentUserService currentUserService) {
        this.mobileAuthService = mobileAuthService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    public MobileTokenResponse login(@Valid @RequestBody(required = false) MobileLoginRequest req) {
        return mobileAuthService.login(req);
    }

    @PostMapping("/refresh")
    public MobileTokenResponse refresh(@Valid @RequestBody(required = false) MobileRefreshRequest req) {
        return mobileAuthService.refresh(req == null ? null : req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) MobileLogoutRequest req,
                                       HttpServletRequest request) {
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            mobileAuthService.logoutByRefreshToken(req.refreshToken());
        } else {
            mobileAuthService.logoutCurrentAccessToken(extractBearer(request));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MobileUserDto me(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return new MobileUserDto(current.getUsername());
    }

    @GetMapping("/sessions")
    public List<MobileAuthTokenDto> sessions(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return mobileAuthService.sessions(current);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        mobileAuthService.revokeSession(current, id);
        return ResponseEntity.noContent().build();
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }
}
