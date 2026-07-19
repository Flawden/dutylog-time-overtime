package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import ru.daniil.shifts.dto.Dtos.PageDto;

import java.util.List;
import java.util.Locale;

/**
 * Админское управление пользователями.
 * Сейчас поддерживаем только роли доступа USER/ADMIN.
 * accountTier хранится отдельно как задел под будущие FREE/PAID/VIP и не влияет на права.
 */
@Service
public class UserAdminService {
    public static final List<String> ALLOWED_ROLES = List.of("USER", "ADMIN");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MobileAuthService mobileAuthService;
    private final SecurityEventLogger securityEvents;
    private final String bootstrapAdminUsername;

    public UserAdminService(UserRepository users,
                            PasswordEncoder encoder,
                            MobileAuthService mobileAuthService,
                            SecurityEventLogger securityEvents,
                            @Value("${dutylog.admin.username:}") String bootstrapAdminUsername) {
        this.users = users;
        this.encoder = encoder;
        this.mobileAuthService = mobileAuthService;
        this.securityEvents = securityEvents;
        this.bootstrapAdminUsername = bootstrapAdminUsername == null ? "" : bootstrapAdminUsername.trim();
    }

    public record AdminUserDto(
            Long id,
            String username,
            String displayName,
            String role,
            String accountTier,
            boolean bootstrapAdmin,
            boolean currentUser,
            String createdAt,
            String updatedAt
    ) {}

    @Transactional(readOnly = true)
    public PageDto<AdminUserDto> listUsers(AppUser currentUser, int page, int size, String query, String role) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        String q = clean(query);
        String roleFilter = clean(role);
        if (roleFilter != null) roleFilter = roleFilter.toUpperCase(Locale.ROOT);
        final String roleFinal = roleFilter;

        List<AdminUserDto> filtered = users.findAllByOrderByIdAsc().stream()
                .filter(user -> roleFinal == null || "ALL".equals(roleFinal) || roleFinal.equalsIgnoreCase(user.getRole()))
                .filter(user -> q == null
                        || contains(user.getUsername(), q)
                        || contains(user.getDisplayName(), q)
                        || contains(user.getRole(), q)
                        || contains(user.getAccountTier(), q))
                .map(user -> toDto(user, currentUser))
                .toList();
        return PageDto.of(pageSlice(filtered, safePage, safeSize), safePage, safeSize, filtered.size());
    }

    @Transactional(readOnly = true)
    public long adminCount() {
        return users.countByRoleIgnoreCase("ADMIN");
    }

    @Transactional(readOnly = true)
    public long userCount() {
        return users.count();
    }

    @Transactional
    public AdminUserDto changeRole(Long userId, String requestedRole, AppUser currentUser) {
        AppUser target = findTarget(userId);
        String role = normalizeRole(requestedRole);

        if ("USER".equals(role) && target.isAdmin()) {
            if (isBootstrapAdmin(target)) {
                throw ApiException.badRequest("Стартового env-админа нельзя понизить до USER");
            }
            if (target.getId().equals(currentUser.getId())) {
                throw ApiException.badRequest("Нельзя понизить собственную роль из активной админской сессии");
            }
            if (adminCount() <= 1) {
                throw ApiException.badRequest("Нельзя убрать последнего администратора");
            }
        }

        String previousRole = target.getRole();
        target.setRole(role);
        if (!previousRole.equalsIgnoreCase(role)) {
            target.bumpAuthVersion();
        }
        AppUser saved = users.save(target);
        securityEvents.info("ADMIN_ROLE_CHANGED", currentUser.getUsername(), "accepted",
                "target=" + target.getUsername() + " from=" + previousRole + " to=" + role);
        return toDto(saved, currentUser);
    }

    @Transactional
    public AdminUserDto resetPassword(Long userId, String newPassword, AppUser currentUser) {
        AppUser target = findTarget(userId);
        String password = newPassword == null ? "" : newPassword;
        if (password.length() < 12) {
            throw ApiException.badRequest("Новый пароль: минимум 12 символов для админского сброса");
        }
        target.setPasswordHash(encoder.encode(password));
        target.bumpAuthVersion();
        AppUser saved = users.save(target);
        mobileAuthService.revokeAllSessions(saved);
        securityEvents.warn("ADMIN_PASSWORD_RESET", currentUser.getUsername(), "accepted",
                "target=" + target.getUsername());
        return toDto(saved, currentUser);
    }


    private int safePage(int page) {
        return Math.max(0, page);
    }

    private int safeSize(int size) {
        if (size <= 0) return 50;
        return Math.min(100, Math.max(10, size));
    }

    private <T> List<T> pageSlice(List<T> list, int page, int size) {
        int from = Math.min(list.size(), page * size);
        int to = Math.min(list.size(), from + size);
        return list.subList(from, to);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
    }

    private AppUser findTarget(Long userId) {
        if (userId == null) {
            throw ApiException.badRequest("Не указан id пользователя");
        }
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    private String normalizeRole(String requestedRole) {
        String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw ApiException.badRequest("Роль должна быть USER или ADMIN");
        }
        return role;
    }

    private AdminUserDto toDto(AppUser user, AppUser currentUser) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getAccountTier(),
                isBootstrapAdmin(user),
                currentUser != null && user.getId().equals(currentUser.getId()),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null
        );
    }

    private boolean isBootstrapAdmin(AppUser user) {
        return !bootstrapAdminUsername.isBlank() && user.getUsername().equalsIgnoreCase(bootstrapAdminUsername);
    }
}
