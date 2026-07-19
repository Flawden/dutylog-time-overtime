package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;

import java.time.Clock;
import java.time.Instant;

/** Keeps expired and revoked mobile-session rows from growing without bound. */
@Service
public class MobileAuthTokenCleanupService {
    private final MobileAuthTokenRepository tokens;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public MobileAuthTokenCleanupService(
            MobileAuthTokenRepository tokens,
            @Value("${dutylog.mobile.auth-token.retention-days:90}") int retentionDays) {
        this(tokens, retentionDays, Clock.systemUTC());
    }

    MobileAuthTokenCleanupService(MobileAuthTokenRepository tokens, int retentionDays, Clock clock) {
        this.tokens = tokens;
        this.retentionDays = Math.max(7, retentionDays);
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${dutylog.mobile.auth-token.cleanup-delay-ms:86400000}",
            initialDelayString = "${dutylog.mobile.auth-token.cleanup-initial-delay-ms:120000}")
    @Transactional
    public void cleanupExpiredTokens() {
        cleanupNow();
    }

    long cleanupNow() {
        Instant threshold = clock.instant().minusSeconds(retentionDays * 86400L);
        return tokens.deleteByRefreshExpiresAtBefore(threshold);
    }
}
