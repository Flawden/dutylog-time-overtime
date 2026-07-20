package ru.daniil.shifts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppSetting;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AppSettingRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.regex.Pattern;

/**
 * Production-safe bootstrap администратора.
 *
 * Важно:
 * - публичная регистрация никогда не выдаёт ADMIN сама по себе;
 * - стартовый админ создаётся из DUTYLOG_ADMIN_USERNAME + DUTYLOG_ADMIN_PASSWORD;
 * - после создания пароль можно менять в UI, и обычный рестарт не затрёт его env-паролем;
 * - дополнительных админов назначает уже админ через админку.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-zА-Яа-яЁё0-9_.-]+");
    private static final String LEGACY_ADMIN_CLEANUP_DONE = "admin.roles.v22_3_legacyCleanupDone";

    private final UserRepository users;
    private final AppSettingRepository settings;
    private final PasswordEncoder encoder;
    private final DefaultShiftSeedService defaultShiftSeedService;
    private final MobileAuthService mobileAuthService;
    private final RememberMeTokenService rememberMeTokenService;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean forcePasswordReset;

    public AdminBootstrapService(UserRepository users,
                                 AppSettingRepository settings,
                                 PasswordEncoder encoder,
                                 DefaultShiftSeedService defaultShiftSeedService,
                                 MobileAuthService mobileAuthService,
                                 RememberMeTokenService rememberMeTokenService,
                                 @Value("${dutylog.admin.username:}") String adminUsername,
                                 @Value("${dutylog.admin.password:}") String adminPassword,
                                 @Value("${dutylog.admin.force-password-reset:false}") boolean forcePasswordReset) {
        this.users = users;
        this.settings = settings;
        this.encoder = encoder;
        this.defaultShiftSeedService = defaultShiftSeedService;
        this.mobileAuthService = mobileAuthService;
        this.rememberMeTokenService = rememberMeTokenService;
        this.adminUsername = adminUsername == null ? "" : adminUsername.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.forcePasswordReset = forcePasswordReset;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmin() {
        boolean hasUsername = !adminUsername.isBlank();
        boolean hasPassword = !adminPassword.isBlank();

        if (!hasUsername && !hasPassword) {
            log.info("DutyLog admin bootstrap is not configured; public registration creates USER accounts only.");
            return;
        }
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("DUTYLOG_ADMIN_USERNAME and DUTYLOG_ADMIN_PASSWORD must be set together");
        }

        validateAdminCredentials(adminUsername, adminPassword);

        AppUser user = users.findByUsername(adminUsername).orElse(null);
        if (user == null) {
            user = new AppUser(adminUsername, encoder.encode(adminPassword));
            user.setRole("ADMIN");
            user.setAccountTier("FREE");
            user = users.save(user);
            defaultShiftSeedService.seedDefaults(user);
            int legacyDemoted = cleanupLegacyAdminsOnce(adminUsername);
            log.info("DutyLog bootstrap administrator '{}' created from environment. Legacy admins demoted once: {}.", adminUsername, legacyDemoted);
            return;
        }

        boolean promoted = !user.isAdmin();
        user.setRole("ADMIN");
        if (forcePasswordReset) {
            user.setPasswordHash(encoder.encode(adminPassword));
            log.warn("DutyLog bootstrap administrator '{}' password was reset from environment because dutylog.admin.force-password-reset=true.", adminUsername);
        }
        if (promoted || forcePasswordReset) {
            user.bumpAuthVersion();
        }
        users.save(user);
        if (forcePasswordReset) {
            mobileAuthService.revokeAllSessions(user);
        }
        if (promoted || forcePasswordReset) {
            rememberMeTokenService.revokeAll(user);
        }

        int legacyDemoted = cleanupLegacyAdminsOnce(adminUsername);
        if (promoted) {
            log.info("DutyLog bootstrap user '{}' promoted to ADMIN from environment. Existing password kept unless force reset is enabled. Legacy admins demoted once: {}.", adminUsername, legacyDemoted);
        } else {
            log.info("DutyLog bootstrap administrator '{}' ensured from environment. Existing password kept unless force reset is enabled. Legacy admins demoted once: {}.", adminUsername, legacyDemoted);
        }
    }

    /**
     * One-time cleanup for installations that still carried the old MVP rule
     * “first user is admin”. After v22.3 this must not run on every restart,
     * because admins may be legitimately assigned from the admin UI.
     */
    private int cleanupLegacyAdminsOnce(String expectedUsername) {
        if (settings.existsById(LEGACY_ADMIN_CLEANUP_DONE)) {
            return 0;
        }
        int demoted = 0;
        for (AppUser user : users.findAll()) {
            if (user.isAdmin() && !user.getUsername().equalsIgnoreCase(expectedUsername)) {
                user.setRole("USER");
                user.bumpAuthVersion();
                users.save(user);
                rememberMeTokenService.revokeAll(user);
                demoted++;
            }
        }
        settings.save(new AppSetting(LEGACY_ADMIN_CLEANUP_DONE, java.time.Instant.now().toString()));
        return demoted;
    }

    private void validateAdminCredentials(String username, String password) {
        if (username.length() < 3 || username.length() > 40 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("DUTYLOG_ADMIN_USERNAME must be 3-40 chars and contain only letters, digits, dot, dash or underscore");
        }
        if (password.length() < 20) {
            throw new IllegalStateException("DUTYLOG_ADMIN_PASSWORD must be at least 20 characters long");
        }
    }
}
