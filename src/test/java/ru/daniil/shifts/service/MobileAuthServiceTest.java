package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.dto.Dtos.MobileLoginRequest;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural coverage for Android access/refresh token lifecycle. */
@SpringBootTest
@Transactional
class MobileAuthServiceTest {

    @Autowired MobileAuthService mobileAuthService;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("mobile-auth-owner", encoder.encode("secret123")));
        other = users.save(new AppUser("mobile-auth-other", encoder.encode("other123")));
    }

    @Test
    void loginTrimsUsernameNormalizesDeviceAndStoresOnlyTokenHashes() {
        MobileTokenResponse response = mobileAuthService.login(
                new MobileLoginRequest("  mobile-auth-owner  ", "secret123", "  Pixel 9 Pro  "));

        assertEquals("Bearer", response.tokenType());
        assertEquals(owner.getUsername(), response.user().username());
        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals(response.accessToken(), response.refreshToken());

        MobileAuthToken stored = tokens.findByAccessTokenHash(MobileAuthService.hash(response.accessToken()))
                .orElseThrow();
        assertEquals(owner.getId(), stored.getOwner().getId());
        assertEquals("Pixel 9 Pro", stored.getDeviceName());
        assertEquals(MobileAuthService.hash(response.refreshToken()), stored.getRefreshTokenHash());
        assertNotEquals(response.accessToken(), stored.getAccessTokenHash());
        assertNotEquals(response.refreshToken(), stored.getRefreshTokenHash());
        assertTrue(stored.getAccessExpiresAt().isAfter(Instant.now()));
        assertTrue(stored.getRefreshExpiresAt().isAfter(stored.getAccessExpiresAt()));
        assertEquals(owner.getId(), mobileAuthService.authenticateAccessToken(response.accessToken())
                .orElseThrow().getId());
    }

    @Test
    void nullUnknownAndWrongCredentialsUseStableErrors() {
        assertBadRequest(() -> mobileAuthService.login(null));

        ApiException unknown = assertThrows(ApiException.class, () -> mobileAuthService.login(
                new MobileLoginRequest("missing-user", "secret123", "Phone")));
        assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatus());
        assertEquals("AUTH_INVALID_CREDENTIALS", unknown.getCode());

        ApiException wrong = assertThrows(ApiException.class, () -> mobileAuthService.login(
                new MobileLoginRequest(owner.getUsername(), "wrong", "Phone")));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatus());
        assertEquals("AUTH_INVALID_CREDENTIALS", wrong.getCode());
    }

    @Test
    void refreshRotatesBothTokensInPlaceAndInvalidatesTheOldPair() {
        MobileTokenResponse first = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Phone");
        MobileAuthToken before = tokens.findByRefreshTokenHash(MobileAuthService.hash(first.refreshToken()))
                .orElseThrow();
        Long tokenRowId = before.getId();
        String oldAccessHash = before.getAccessTokenHash();
        String oldRefreshHash = before.getRefreshTokenHash();

        MobileTokenResponse rotated = mobileAuthService.refresh(first.refreshToken());
        MobileAuthToken after = tokens.findById(tokenRowId).orElseThrow();

        assertEquals(1, tokens.findByOwnerOrderByCreatedAtDesc(owner).size());
        assertEquals(tokenRowId, after.getId());
        assertNotEquals(first.accessToken(), rotated.accessToken());
        assertNotEquals(first.refreshToken(), rotated.refreshToken());
        assertNotEquals(oldAccessHash, after.getAccessTokenHash());
        assertNotEquals(oldRefreshHash, after.getRefreshTokenHash());
        assertTrue(mobileAuthService.authenticateAccessToken(first.accessToken()).isEmpty());
        assertEquals(owner.getId(), mobileAuthService.authenticateAccessToken(rotated.accessToken())
                .orElseThrow().getId());

        ApiException replay = assertThrows(ApiException.class,
                () -> mobileAuthService.refresh(first.refreshToken()));
        assertEquals(HttpStatus.UNAUTHORIZED, replay.getStatus());
        assertEquals("TOKEN_INVALID", replay.getCode());
    }

    @Test
    void expiredAndRevokedRefreshTokensAreRejectedAndRemainRevoked() {
        MobileAuthToken expired = tokens.save(new MobileAuthToken(
                owner,
                MobileAuthService.hash("expired-access"),
                MobileAuthService.hash("expired-refresh"),
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60),
                "Expired phone"));

        ApiException expiredError = assertThrows(ApiException.class,
                () -> mobileAuthService.refresh("expired-refresh"));
        assertEquals("TOKEN_INVALID", expiredError.getCode());
        assertTrue(tokens.findById(expired.getId()).orElseThrow().isRevoked());

        MobileAuthToken revoked = new MobileAuthToken(
                owner,
                MobileAuthService.hash("revoked-access"),
                MobileAuthService.hash("revoked-refresh"),
                Instant.now().plusSeconds(120),
                Instant.now().plusSeconds(3600),
                "Revoked phone");
        revoked.revoke();
        revoked = tokens.save(revoked);

        ApiException revokedError = assertThrows(ApiException.class,
                () -> mobileAuthService.refresh("revoked-refresh"));
        assertEquals(HttpStatus.UNAUTHORIZED, revokedError.getStatus());
        assertEquals("TOKEN_INVALID", revokedError.getCode());
        assertTrue(tokens.findById(revoked.getId()).orElseThrow().isRevoked());
    }

    @Test
    void accessAuthenticationRejectsBlankUnknownExpiredAndRevokedTokens() {
        assertTrue(mobileAuthService.authenticateAccessToken(null).isEmpty());
        assertTrue(mobileAuthService.authenticateAccessToken(" ").isEmpty());
        assertTrue(mobileAuthService.authenticateAccessToken("unknown").isEmpty());

        tokens.save(new MobileAuthToken(
                owner,
                MobileAuthService.hash("expired-access-only"),
                MobileAuthService.hash("refresh-a"),
                Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(3600),
                "Expired access"));
        assertTrue(mobileAuthService.authenticateAccessToken("expired-access-only").isEmpty());

        MobileAuthToken revoked = new MobileAuthToken(
                owner,
                MobileAuthService.hash("revoked-access-only"),
                MobileAuthService.hash("refresh-b"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "Revoked access");
        revoked.revoke();
        tokens.save(revoked);
        assertTrue(mobileAuthService.authenticateAccessToken("revoked-access-only").isEmpty());
    }

    @Test
    void touchAccessTokenIsThrottledButUpdatesStaleSessions() {
        MobileTokenResponse response = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Phone");
        MobileAuthToken stored = tokens.findByAccessTokenHash(MobileAuthService.hash(response.accessToken()))
                .orElseThrow();
        assertEquals(null, stored.getLastUsedAt());

        mobileAuthService.touchAccessToken(response.accessToken());
        Instant firstTouch = tokens.findById(stored.getId()).orElseThrow().getLastUsedAt();
        assertNotNull(firstTouch);

        mobileAuthService.touchAccessToken(response.accessToken());
        assertEquals(firstTouch, tokens.findById(stored.getId()).orElseThrow().getLastUsedAt());

        MobileAuthToken stale = tokens.findById(stored.getId()).orElseThrow();
        stale.setLastUsedAt(Instant.now().minusSeconds(301));
        tokens.saveAndFlush(stale);
        Instant staleValue = stale.getLastUsedAt();

        mobileAuthService.touchAccessToken(response.accessToken());
        assertTrue(tokens.findById(stored.getId()).orElseThrow().getLastUsedAt().isAfter(staleValue));
    }

    @Test
    void logoutByRefreshAndAccessAreIdempotentAndDoNotTouchOtherUsers() {
        MobileTokenResponse first = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Phone");
        MobileTokenResponse second = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Tablet");
        MobileTokenResponse foreign = mobileAuthService.issueTokenPairForRegisteredUser(other, "Other phone");

        mobileAuthService.logoutByRefreshToken(first.refreshToken());
        mobileAuthService.logoutByRefreshToken(first.refreshToken());
        mobileAuthService.logoutCurrentAccessToken(second.accessToken());
        mobileAuthService.logoutCurrentAccessToken(second.accessToken());
        assertDoesNotThrow(() -> mobileAuthService.logoutByRefreshToken(null));
        assertDoesNotThrow(() -> mobileAuthService.logoutCurrentAccessToken(" "));

        assertTrue(mobileAuthService.authenticateAccessToken(first.accessToken()).isEmpty());
        assertTrue(mobileAuthService.authenticateAccessToken(second.accessToken()).isEmpty());
        assertTrue(mobileAuthService.authenticateAccessToken(foreign.accessToken()).isPresent());
    }

    @Test
    void sessionListIsOwnerScopedAndCalculatesActiveFromRevocationAndRefreshExpiry() {
        MobileTokenResponse activeResponse = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Active phone");
        MobileAuthToken active = tokens.findByAccessTokenHash(MobileAuthService.hash(activeResponse.accessToken()))
                .orElseThrow();

        MobileAuthToken expired = tokens.save(new MobileAuthToken(
                owner,
                MobileAuthService.hash("session-expired-access"),
                MobileAuthService.hash("session-expired-refresh"),
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(1),
                "Expired phone"));
        MobileAuthToken revoked = new MobileAuthToken(
                owner,
                MobileAuthService.hash("session-revoked-access"),
                MobileAuthService.hash("session-revoked-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "Revoked phone");
        revoked.revoke();
        tokens.save(revoked);
        mobileAuthService.issueTokenPairForRegisteredUser(other, "Foreign phone");

        List<MobileAuthTokenDto> sessions = mobileAuthService.sessions(owner);
        assertEquals(3, sessions.size());
        assertTrue(sessions.stream().anyMatch(s -> s.id().equals(active.getId()) && s.active() && !s.revoked()));
        assertTrue(sessions.stream().anyMatch(s -> s.id().equals(expired.getId()) && !s.active() && !s.revoked()));
        assertTrue(sessions.stream().anyMatch(s -> s.id().equals(revoked.getId()) && !s.active() && s.revoked()));
        assertFalse(sessions.stream().anyMatch(s -> "Foreign phone".equals(s.deviceName())));
    }

    @Test
    void revokeAllAndSingleSessionRespectOwnershipAndStableErrors() {
        MobileTokenResponse ownerOne = mobileAuthService.issueTokenPairForRegisteredUser(owner, "One");
        MobileTokenResponse ownerTwo = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Two");
        MobileTokenResponse foreign = mobileAuthService.issueTokenPairForRegisteredUser(other, "Foreign");
        MobileAuthToken ownerToken = tokens.findByAccessTokenHash(MobileAuthService.hash(ownerOne.accessToken()))
                .orElseThrow();
        MobileAuthToken foreignToken = tokens.findByAccessTokenHash(MobileAuthService.hash(foreign.accessToken()))
                .orElseThrow();

        assertBadRequest(() -> mobileAuthService.revokeSession(owner, null));
        assertNotFound(() -> mobileAuthService.revokeSession(owner, foreignToken.getId()));
        assertNotFound(() -> mobileAuthService.revokeSession(owner, Long.MAX_VALUE));

        mobileAuthService.revokeSession(owner, ownerToken.getId());
        mobileAuthService.revokeSession(owner, ownerToken.getId());
        mobileAuthService.revokeAllSessions(owner);

        assertTrue(tokens.findByOwnerOrderByCreatedAtDesc(owner).stream().allMatch(MobileAuthToken::isRevoked));
        assertTrue(mobileAuthService.authenticateAccessToken(ownerTwo.accessToken()).isEmpty());
        assertTrue(mobileAuthService.authenticateAccessToken(foreign.accessToken()).isPresent());
    }

    @Test
    void registeredUserIssuanceUsesSafeDefaultAndTruncatesLongDeviceNames() {
        assertBadRequest(() -> mobileAuthService.issueTokenPairForRegisteredUser(null, "Phone"));

        MobileTokenResponse defaultDevice = mobileAuthService.issueTokenPairForRegisteredUser(owner, "   ");
        MobileAuthToken defaultStored = tokens.findByAccessTokenHash(MobileAuthService.hash(defaultDevice.accessToken()))
                .orElseThrow();
        assertEquals("Android", defaultStored.getDeviceName());

        String longName = "x".repeat(140);
        MobileTokenResponse longDevice = mobileAuthService.issueTokenPairForRegisteredUser(owner, longName);
        MobileAuthToken longStored = tokens.findByAccessTokenHash(MobileAuthService.hash(longDevice.accessToken()))
                .orElseThrow();
        assertEquals(120, longStored.getDeviceName().length());
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    private static void assertNotFound(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("NOT_FOUND", error.getCode());
    }
}
