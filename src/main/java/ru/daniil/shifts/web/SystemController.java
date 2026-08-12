package ru.daniil.shifts.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AppSettingsService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.UserAdminService;
import ru.daniil.shifts.service.exception.ApiException;
import ru.daniil.shifts.telegram.TelegramLinkService;
import ru.daniil.shifts.telegram.TelegramLinkService.TelegramStatusDto;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * Служебная диагностика для администратора.
 * Ничего секретного не отдаёт: токены, пароли и URL БД здесь не светятся.
 */
@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
public class SystemController {
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final TelegramLinkService telegramLinkService;
    private final AppSettingsService appSettingsService;
    private final UserAdminService userAdminService;

    @Value("${dutylog.telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${dutylog.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${dutylog.telegram.polling-enabled:false}")
    private boolean telegramPollingEnabled;

    @Value("${dutylog.telegram.notifications-enabled:true}")
    private boolean telegramNotificationsEnabled;

    @Value("${info.app.version:dev}")
    private String appVersion;

    public SystemController(Environment environment,
                            JdbcTemplate jdbcTemplate,
                            CurrentUserService currentUserService,
                            TelegramLinkService telegramLinkService,
                            AppSettingsService appSettingsService,
                            UserAdminService userAdminService) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.telegramLinkService = telegramLinkService;
        this.appSettingsService = appSettingsService;
        this.userAdminService = userAdminService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(Principal principal) {
        AppUser user = requireAdmin(principal);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", "DutyLog: Time & Overtime");
        result.put("version", appVersion);
        result.put("admin", user.getUsername());
        result.put("serverTime", Instant.now().toString());
        result.put("serverTimezone", ZoneId.systemDefault().toString());
        result.put("profiles", Arrays.asList(environment.getActiveProfiles()));
        result.put("database", databaseStatus());
        result.put("users", userManagementStatus());
        result.put("registration", appSettingsService.registrationStatus());
        result.put("telegram", telegramStatus(principal));
        return result;
    }



    public record UserRoleRequest(String role) {}
    public record UserPasswordResetRequest(String newPassword) {}

    @GetMapping("/users")
    public PageDto<UserAdminService.AdminUserDto> users(@RequestParam(name = "page", required = false, defaultValue = "0") int page,
                                                        @RequestParam(name = "size", required = false, defaultValue = "50") int size,
                                                        @RequestParam(name = "q", required = false) String q,
                                                        @RequestParam(name = "role", required = false, defaultValue = "all") String role,
                                                        Principal principal) {
        AppUser admin = requireAdmin(principal);
        return userAdminService.listUsers(admin, page, size, q, role);
    }

    @PatchMapping("/users/{id}/role")
    public UserAdminService.AdminUserDto updateUserRole(@PathVariable("id") Long id,
                                                        @RequestBody UserRoleRequest request,
                                                        Principal principal) {
        AppUser admin = requireAdmin(principal);
        if (request == null) {
            throw ApiException.badRequest("Нужно передать role");
        }
        return userAdminService.changeRole(id, request.role(), admin);
    }

    @PostMapping("/users/{id}/password")
    public UserAdminService.AdminUserDto resetUserPassword(@PathVariable("id") Long id,
                                                           @RequestBody UserPasswordResetRequest request,
                                                           Principal principal) {
        AppUser admin = requireAdmin(principal);
        if (request == null) {
            throw ApiException.badRequest("Нужно передать newPassword");
        }
        return userAdminService.resetPassword(id, request.newPassword(), admin);
    }


    public record RegistrationSettingsRequest(Boolean enabled) {}

    @GetMapping("/settings/registration")
    public Map<String, Object> registrationSettings(Principal principal) {
        requireAdmin(principal);
        return appSettingsService.registrationStatus();
    }

    @PatchMapping("/settings/registration")
    public Map<String, Object> updateRegistrationSettings(@RequestBody RegistrationSettingsRequest request, Principal principal) {
        AppUser user = requireAdmin(principal);
        if (request == null || request.enabled() == null) {
            throw ApiException.badRequest("Нужно передать enabled: true/false");
        }
        appSettingsService.setRegistrationEnabled(request.enabled(), user.getUsername());
        return appSettingsService.registrationStatus();
    }

    private AppUser requireAdmin(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        if (!user.isAdmin()) {
            throw ApiException.forbidden("Диагностика доступна только администратору");
        }
        return user;
    }

    private Map<String, Object> userManagementStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", userAdminService.userCount());
        out.put("admins", userAdminService.adminCount());
        out.put("rolesAllowed", UserAdminService.ALLOWED_ROLES);
        out.put("accountTiersReserved", List.of("FREE", "PAID", "VIP"));
        return out;
    }

    private Map<String, Object> databaseStatus() {
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            db.put("ok", one != null && one == 1);
        } catch (Exception e) {
            db.put("ok", false);
            db.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return db;
    }

    private Map<String, Object> telegramStatus(Principal principal) {
        Map<String, Object> tg = new LinkedHashMap<>();
        tg.put("enabled", telegramEnabled);
        tg.put("tokenConfigured", telegramBotToken != null && !telegramBotToken.isBlank());
        tg.put("pollingEnabled", telegramPollingEnabled);
        tg.put("notificationsEnabled", telegramNotificationsEnabled);
        try {
            AppUser user = currentUserService.requireUser(principal);
            TelegramStatusDto status = telegramLinkService.status(user);
            tg.put("configured", status.configured());
            tg.put("linked", status.linked());
            tg.put("accountNotificationsEnabled", status.notificationsEnabled());
            tg.put("botUsername", status.botUsername());
        } catch (Exception ignored) {
            tg.put("linked", false);
        }
        return tg;
    }
}
