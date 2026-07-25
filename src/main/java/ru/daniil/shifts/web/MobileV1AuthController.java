package ru.daniil.shifts.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.dto.Dtos.MobileLoginRequest;
import ru.daniil.shifts.dto.Dtos.MobileLogoutRequest;
import ru.daniil.shifts.dto.Dtos.MobileRefreshRequest;
import ru.daniil.shifts.dto.Dtos.MobileRegisterRequest;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.dto.Dtos.MobileUserDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AppSettingsService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.service.UserRegistrationService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Stable Android authentication contract. Breaking changes require /api/v2. */
@RestController
@RequestMapping("/api/v1/mobile/auth")
public class MobileV1AuthController {
    private final MobileAuthService mobileAuthService;
    private final CurrentUserService currentUserService;
    private final UserRegistrationService registrationService;
    private final AppSettingsService appSettingsService;

    public MobileV1AuthController(MobileAuthService mobileAuthService,
                                  CurrentUserService currentUserService,
                                  UserRegistrationService registrationService,
                                  AppSettingsService appSettingsService) {
        this.mobileAuthService = mobileAuthService;
        this.currentUserService = currentUserService;
        this.registrationService = registrationService;
        this.appSettingsService = appSettingsService;
    }

    @PostMapping("/login")
    public MobileTokenResponse login(@Valid @RequestBody(required = false) MobileLoginRequest request) {
        return mobileAuthService.login(request);
    }

    @PostMapping("/register")
    public ResponseEntity<MobileTokenResponse> register(
            @Valid @RequestBody(required = false) MobileRegisterRequest request) {
        if (request == null) {
            throw ru.daniil.shifts.service.exception.ApiException.badRequest(
                    "INVALID_JSON", "Некорректный JSON в запросе");
        }
        AppUser user = registrationService.register(
                request.username(), request.password(), request.languagePreference(), "mobile-v1");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mobileAuthService.issueTokenPairForRegisteredUser(user, request.deviceName()));
    }

    @GetMapping("/registration-status")
    public Map<String, Object> registrationStatus() {
        return appSettingsService.registrationStatus();
    }

    @PostMapping("/refresh")
    public MobileTokenResponse refresh(@Valid @RequestBody(required = false) MobileRefreshRequest request) {
        return mobileAuthService.refresh(request == null ? null : request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) MobileLogoutRequest request,
                                       HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            mobileAuthService.logoutByRefreshToken(request.refreshToken());
        } else {
            mobileAuthService.logoutCurrentAccessToken(extractBearer(servletRequest));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MobileUserDto me(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return MobileUserDto.from(current);
    }

    @GetMapping("/sessions")
    public List<MobileAuthTokenDto> sessions(Principal principal) {
        return mobileAuthService.sessions(currentUserService.requireUser(principal));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable("id") Long id, Principal principal) {
        mobileAuthService.revokeSession(currentUserService.requireUser(principal), id);
        return ResponseEntity.noContent().build();
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring("Bearer ".length()).trim();
    }
}
