package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileAuthTokenCleanupServiceTest {

    @Test
    void cleanupDeletesOnlyRowsOlderThanConfiguredRetention() {
        MobileAuthTokenRepository tokens = mock(MobileAuthTokenRepository.class);
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        Instant threshold = now.minusSeconds(30L * 86400L);
        when(tokens.deleteByRefreshExpiresAtBefore(threshold)).thenReturn(7L);

        MobileAuthTokenCleanupService service = new MobileAuthTokenCleanupService(
                tokens, 30, Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(7L, service.cleanupNow());
        verify(tokens).deleteByRefreshExpiresAtBefore(threshold);
    }

    @Test
    void unsafeTinyRetentionIsClampedToSevenDays() {
        MobileAuthTokenRepository tokens = mock(MobileAuthTokenRepository.class);
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        Instant threshold = now.minusSeconds(7L * 86400L);

        MobileAuthTokenCleanupService service = new MobileAuthTokenCleanupService(
                tokens, 0, Clock.fixed(now, ZoneOffset.UTC));

        service.cleanupNow();
        verify(tokens).deleteByRefreshExpiresAtBefore(threshold);
    }
}
