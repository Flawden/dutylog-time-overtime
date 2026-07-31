package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.CalendarSubscriptionDto;
import ru.daniil.shifts.dto.Dtos.CalendarSyncStatusDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CalendarFeedSubscription;
import ru.daniil.shifts.repo.CalendarFeedSubscriptionRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Issues, rotates, revokes and resolves private read-only iCalendar feed tokens. */
@Service
public class CalendarSubscriptionService {
    public static final List<String> ENTITIES = List.of("shifts", "tasks", "important_events", "absences");

    private final CalendarFeedSubscriptionRepository subscriptions;
    private final ModuleService moduleService;
    private final SecurityEventLogger securityEvents;
    private final SecureRandom random = new SecureRandom();
    private final int feedPastDays;
    private final int feedFutureDays;

    public CalendarSubscriptionService(
            CalendarFeedSubscriptionRepository subscriptions,
            ModuleService moduleService,
            SecurityEventLogger securityEvents,
            @Value("${dutylog.calendar-sync.feed-past-days:30}") int feedPastDays,
            @Value("${dutylog.calendar-sync.feed-future-days:335}") int feedFutureDays) {
        this.subscriptions = subscriptions;
        this.moduleService = moduleService;
        this.securityEvents = securityEvents;
        this.feedPastDays = Math.max(0, Math.min(365, feedPastDays));
        this.feedFutureDays = Math.max(0, Math.min(365 - this.feedPastDays, feedFutureDays));
    }

    @Transactional(readOnly = true)
    public CalendarSyncStatusDto status(AppUser user) {
        return subscriptions.findByOwner(user)
                .map(this::toStatus)
                .orElseGet(() -> new CalendarSyncStatusDto(
                        false, null, null, null, feedPastDays, feedFutureDays, ENTITIES));
    }

    @Transactional
    public IssueResult issue(AppUser user) {
        Token token = newUniqueToken();
        CalendarFeedSubscription subscription = subscriptions.findByOwner(user)
                .orElseGet(() -> new CalendarFeedSubscription(user, token.hash(), token.hint()));
        if (subscription.getId() != null) {
            subscription.rotate(token.hash(), token.hint());
        }
        CalendarFeedSubscription saved = subscriptions.saveAndFlush(subscription);
        securityEvents.info("CALENDAR_FEED_ROTATED", user.getUsername(), "accepted",
                "tokenHint=" + saved.getTokenHint());
        return new IssueResult(saved, token.raw());
    }

    @Transactional
    public void revoke(AppUser user) {
        subscriptions.findByOwner(user).ifPresent(subscription -> {
            String hint = subscription.getTokenHint();
            subscriptions.delete(subscription);
            subscriptions.flush();
            securityEvents.info("CALENDAR_FEED_REVOKED", user.getUsername(), "accepted",
                    "tokenHint=" + hint);
        });
    }

    @Transactional(readOnly = true)
    public AppUser resolveOwner(String rawToken) {
        String normalized = normalizeRawToken(rawToken);
        CalendarFeedSubscription subscription = subscriptions.findByTokenHash(hash(normalized))
                .orElseThrow(() -> ApiException.notFound("Календарная подписка не найдена"));
        AppUser owner = subscription.getOwner();
        if (!moduleService.isEnabled(owner, ModuleService.CALENDAR_SYNC)) {
            throw ApiException.notFound("Календарная подписка не найдена");
        }
        return owner;
    }

    public DateRange feedRange(LocalDate today) {
        LocalDate safeToday = today == null ? LocalDate.now() : today;
        return new DateRange(safeToday.minusDays(feedPastDays), safeToday.plusDays(feedFutureDays));
    }

    public CalendarSubscriptionDto toIssuedDto(IssueResult result, String subscriptionUrl) {
        CalendarFeedSubscription subscription = result.subscription();
        return new CalendarSubscriptionDto(
                true,
                subscription.getTokenHint(),
                subscription.getCreatedAt().toString(),
                subscription.getRotatedAt().toString(),
                subscriptionUrl,
                feedPastDays,
                feedFutureDays,
                ENTITIES
        );
    }

    private CalendarSyncStatusDto toStatus(CalendarFeedSubscription subscription) {
        return new CalendarSyncStatusDto(
                true,
                subscription.getTokenHint(),
                subscription.getCreatedAt().toString(),
                subscription.getRotatedAt().toString(),
                feedPastDays,
                feedFutureDays,
                ENTITIES
        );
    }

    private Token newUniqueToken() {
        for (int attempt = 0; attempt < 5; attempt++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String digest = hash(raw);
            if (subscriptions.findByTokenHash(digest).isEmpty()) {
                String hint = raw.substring(0, 6) + "…" + raw.substring(raw.length() - 4);
                return new Token(raw, digest, hint);
            }
        }
        throw new IllegalStateException("Could not allocate a unique calendar feed token");
    }

    static String normalizeRawToken(String rawToken) {
        String value = rawToken == null ? "" : rawToken.trim();
        if (!value.matches("[A-Za-z0-9_-]{43,128}")) {
            throw ApiException.notFound("Календарная подписка не найдена");
        }
        return value;
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record Token(String raw, String hash, String hint) {}
    public record IssueResult(CalendarFeedSubscription subscription, String rawToken) {}
    public record DateRange(LocalDate from, LocalDate to) {}
}
