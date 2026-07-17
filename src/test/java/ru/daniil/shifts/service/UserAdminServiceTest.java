package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.UserAdminService.AdminUserDto;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business rules for user search, role safety and administrative password resets. */
@SpringBootTest
@TestPropertySource(properties = "dutylog.admin.username=bootstrap-root")
@Transactional
class UserAdminServiceTest {

    @Autowired UserAdminService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MobileAuthService mobileAuthService;
    @Autowired MobileAuthTokenRepository tokens;

    AppUser bootstrap;
    AppUser currentAdmin;
    AppUser secondAdmin;
    AppUser regular;

    @BeforeEach
    void setUp() {
        bootstrap = admin("bootstrap-root");
        currentAdmin = admin("admin-current");
        secondAdmin = admin("admin-second");
        regular = users.save(new AppUser("regular-alpha", encoder.encode("unused-password")));
        regular.setDisplayName("Alpha Operator");
        regular.setAccountTier("PAID");
        regular = users.save(regular);
    }

    private AppUser admin(String username) {
        AppUser user = new AppUser(username, encoder.encode("admin-password"));
        user.setRole("ADMIN");
        return users.save(user);
    }

    @Test
    void listSupportsCaseInsensitiveSearchRoleFilterAndIdentityFlags() {
        PageDto<AdminUserDto> search = service.listUsers(currentAdmin, 0, 50, "alpha", "user");
        assertEquals(1, search.total());
        assertEquals(regular.getId(), search.items().get(0).id());
        assertEquals("PAID", search.items().get(0).accountTier());

        PageDto<AdminUserDto> admins = service.listUsers(currentAdmin, 0, 50, null, "ADMIN");
        assertEquals(3, admins.total());
        AdminUserDto bootstrapRow = admins.items().stream()
                .filter(row -> row.id().equals(bootstrap.getId())).findFirst().orElseThrow();
        AdminUserDto currentRow = admins.items().stream()
                .filter(row -> row.id().equals(currentAdmin.getId())).findFirst().orElseThrow();
        assertTrue(bootstrapRow.bootstrapAdmin());
        assertTrue(currentRow.currentUser());
        assertFalse(bootstrapRow.currentUser());
    }

    @Test
    void paginationClampsNegativePageAndTinyOrHugeSizes() {
        for (int i = 0; i < 18; i++) {
            users.save(new AppUser("page-user-" + i, encoder.encode("unused-password")));
        }

        PageDto<AdminUserDto> first = service.listUsers(currentAdmin, -5, 1, null, "all");
        assertEquals(0, first.page());
        assertEquals(10, first.size());
        assertEquals(10, first.items().size());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        PageDto<AdminUserDto> huge = service.listUsers(currentAdmin, 0, 5000, null, "all");
        assertEquals(100, huge.size());
        assertEquals(22, huge.items().size());
    }

    @Test
    void promotionAndSafeDemotionPersistNormalizedRoles() {
        AdminUserDto promoted = service.changeRole(regular.getId(), " admin ", currentAdmin);
        assertEquals("ADMIN", promoted.role());
        assertTrue(users.findById(regular.getId()).orElseThrow().isAdmin());

        AdminUserDto demoted = service.changeRole(secondAdmin.getId(), "user", currentAdmin);
        assertEquals("USER", demoted.role());
        assertFalse(users.findById(secondAdmin.getId()).orElseThrow().isAdmin());
    }

    @Test
    void selfBootstrapAndLastAdministratorDemotionsAreRejectedIndependently() {
        assertBadRequest(() -> service.changeRole(currentAdmin.getId(), "USER", currentAdmin));
        assertBadRequest(() -> service.changeRole(bootstrap.getId(), "USER", currentAdmin));

        users.delete(bootstrap);
        users.delete(currentAdmin);
        users.flush();
        AppUser actingRegular = users.save(new AppUser("acting-regular", encoder.encode("unused-password")));
        assertEquals(1, service.adminCount());
        assertBadRequest(() -> service.changeRole(secondAdmin.getId(), "USER", actingRegular));
    }

    @Test
    void missingIdsAndUnknownRolesUseStableClientErrors() {
        assertBadRequest(() -> service.changeRole(null, "ADMIN", currentAdmin));
        assertBadRequest(() -> service.changeRole(regular.getId(), null, currentAdmin));
        assertBadRequest(() -> service.changeRole(regular.getId(), "SUPERUSER", currentAdmin));

        ApiException missing = assertThrows(ApiException.class,
                () -> service.changeRole(Long.MAX_VALUE, "ADMIN", currentAdmin));
        assertEquals("NOT_FOUND", missing.getCode());
    }

    @Test
    void adminPasswordResetHashesNewPasswordAndRevokesEverySession() {
        mobileAuthService.issueTokenPairForRegisteredUser(regular, "Phone");
        mobileAuthService.issueTokenPairForRegisteredUser(regular, "Tablet");

        service.resetPassword(regular.getId(), "new-admin-reset-password", currentAdmin);

        AppUser stored = users.findById(regular.getId()).orElseThrow();
        assertTrue(encoder.matches("new-admin-reset-password", stored.getPasswordHash()));
        List<MobileAuthToken> storedTokens = tokens.findByOwnerOrderByCreatedAtDesc(stored);
        assertEquals(2, storedTokens.size());
        assertTrue(storedTokens.stream().allMatch(MobileAuthToken::isRevoked));
    }

    @Test
    void shortOrMissingAdministrativeResetPasswordIsRejected() {
        assertBadRequest(() -> service.resetPassword(regular.getId(), null, currentAdmin));
        assertBadRequest(() -> service.resetPassword(regular.getId(), "short-pass", currentAdmin));
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals("BAD_REQUEST", error.getCode());
    }
}
