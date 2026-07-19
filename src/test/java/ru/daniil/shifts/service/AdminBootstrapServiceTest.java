package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.daniil.shifts.model.AppSetting;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AppSettingRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Startup contract for the environment-provided bootstrap administrator. */
class AdminBootstrapServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final DefaultShiftSeedService seeds = mock(DefaultShiftSeedService.class);
    private final MobileAuthService mobileAuthService = mock(MobileAuthService.class);

    private AdminBootstrapService service(String username, String password, boolean forceReset) {
        return new AdminBootstrapService(users, settings, encoder, seeds, mobileAuthService,
                username, password, forceReset);
    }

    @Test
    void emptyConfigurationLeavesAccountsUntouched() {
        service("  ", "", false).bootstrapAdmin();

        verify(users, never()).findByUsername(any());
        verify(users, never()).save(any());
        verify(seeds, never()).seedDefaults(any());
    }

    @Test
    void usernameWithoutPasswordIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalStateException.class,
                () -> service("bootstrap-root", "", false).bootstrapAdmin());
        verify(users, never()).findByUsername(any());
    }

    @Test
    void passwordWithoutUsernameIsRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalStateException.class,
                () -> service("", "a-very-long-bootstrap-password", false).bootstrapAdmin());
        verify(users, never()).findByUsername(any());
    }

    @Test
    void malformedBootstrapUsernamesAreRejected() {
        String password = "a-very-long-bootstrap-password";

        assertThrows(IllegalStateException.class, () -> service("ab", password, false).bootstrapAdmin());
        assertThrows(IllegalStateException.class, () -> service("bad user", password, false).bootstrapAdmin());
        assertThrows(IllegalStateException.class,
                () -> service("x".repeat(41), password, false).bootstrapAdmin());
    }

    @Test
    void shortBootstrapPasswordIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> service("bootstrap-root", "too-short", false).bootstrapAdmin());
        verify(encoder, never()).encode(any());
    }

    @Test
    void missingBootstrapAccountIsCreatedSeededAndLegacyAdminsAreDemotedOnce() {
        AppUser legacyAdmin = new AppUser("legacy-admin", "legacy-hash");
        legacyAdmin.setRole("ADMIN");
        AppUser regular = new AppUser("regular", "regular-hash");
        when(users.findByUsername("bootstrap-root")).thenReturn(Optional.empty());
        when(encoder.encode("a-very-long-bootstrap-password")).thenReturn("encoded-bootstrap");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settings.existsById("admin.roles.v22_3_legacyCleanupDone")).thenReturn(false);
        when(users.findAll()).thenReturn(List.of(legacyAdmin, regular));

        service(" bootstrap-root ", "a-very-long-bootstrap-password", false).bootstrapAdmin();

        verify(seeds).seedDefaults(any(AppUser.class));
        assertFalse(legacyAdmin.isAdmin());
        assertEquals("USER", legacyAdmin.getRole());
        assertEquals(1L, legacyAdmin.getAuthVersion());
        verify(settings).save(any(AppSetting.class));
        verify(users).findByUsername("bootstrap-root");
    }

    @Test
    void existingRegularUserIsPromotedWithoutReplacingPassword() {
        AppUser existing = new AppUser("bootstrap-root", "old-hash");
        when(users.findByUsername("bootstrap-root")).thenReturn(Optional.of(existing));
        when(settings.existsById("admin.roles.v22_3_legacyCleanupDone")).thenReturn(true);

        service("bootstrap-root", "a-very-long-bootstrap-password", false).bootstrapAdmin();

        assertTrue(existing.isAdmin());
        assertEquals("old-hash", existing.getPasswordHash());
        assertEquals(1L, existing.getAuthVersion());
        verify(encoder, never()).encode(any());
        verify(seeds, never()).seedDefaults(any());
        verify(users).save(existing);
    }

    @Test
    void forceResetReplacesExistingBootstrapPassword() {
        AppUser existing = new AppUser("bootstrap-root", "old-hash");
        existing.setRole("ADMIN");
        when(users.findByUsername("bootstrap-root")).thenReturn(Optional.of(existing));
        when(settings.existsById("admin.roles.v22_3_legacyCleanupDone")).thenReturn(true);
        when(encoder.encode("a-very-long-bootstrap-password")).thenReturn("new-hash");

        service("bootstrap-root", "a-very-long-bootstrap-password", true).bootstrapAdmin();

        assertEquals("new-hash", existing.getPasswordHash());
        assertEquals(1L, existing.getAuthVersion());
        verify(encoder).encode("a-very-long-bootstrap-password");
        verify(users).save(existing);
        verify(mobileAuthService).revokeAllSessions(existing);
    }

    @Test
    void alreadyConfiguredAdminRemainsAdminAndCleanupMarkerPreventsRepeatedDemotion() {
        AppUser existing = new AppUser("bootstrap-root", "old-hash");
        existing.setRole("ADMIN");
        when(users.findByUsername("bootstrap-root")).thenReturn(Optional.of(existing));
        when(settings.existsById("admin.roles.v22_3_legacyCleanupDone")).thenReturn(true);

        service("bootstrap-root", "a-very-long-bootstrap-password", false).bootstrapAdmin();

        assertTrue(existing.isAdmin());
        verify(users, never()).findAll();
        verify(settings, never()).save(any());
    }
}
