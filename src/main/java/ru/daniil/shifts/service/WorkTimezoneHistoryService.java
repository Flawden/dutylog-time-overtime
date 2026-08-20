package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimezoneTerm;
import ru.daniil.shifts.repo.WorkTimezoneTermRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Canonical effective-dated resolver for historical work timezone.
 *
 * This service deliberately does not mutate AppUser.workTimezone and does not
 * reconcile downstream facts yet. The returned ChangeWindow is the exact
 * boundary consumed by the Step 2B reconciler.
 */
@Service
public class WorkTimezoneHistoryService {

    public static final LocalDateTime BASELINE_EFFECTIVE_FROM =
            LocalDate.of(1970, 1, 1).atStartOfDay();

    private final WorkTimezoneTermRepository terms;

    public WorkTimezoneHistoryService(
            WorkTimezoneTermRepository terms
    ) {
        this.terms = terms;
    }

    /**
     * Resolve the IANA zone that owns one local work-context moment.
     */
    @Transactional
    public ZoneId zoneAt(
            AppUser user,
            LocalDateTime localMoment
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (localMoment == null) {
            throw new IllegalArgumentException("localMoment is required");
        }

        ensureBaseline(user);

        return terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        localMoment
                )
                .map(WorkTimezoneTerm::getTimezoneId)
                .map(this::safePersistedZone)
                .orElseGet(() -> safePersistedZone(user.getWorkTimezone()));
    }

    public ZoneId zoneAt(
            AppUser user,
            LocalDate date
    ) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        return zoneAt(user, date.atStartOfDay());
    }

    /**
     * First Work Context boundary strictly after this local moment.
     *
     * A factual interval may not silently cross such a boundary because one
     * Actual Work fact must have one unambiguous source timezone.
     */
    @Transactional
    public Optional<LocalDateTime> nextEffectiveFromAfter(
            AppUser user,
            LocalDateTime localMoment
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (localMoment == null) {
            throw new IllegalArgumentException("localMoment is required");
        }

        ensureBaseline(user);

        return terms
                .findFirstByOwnerAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
                        user,
                        localMoment
                )
                .map(WorkTimezoneTerm::getEffectiveFrom);
    }

    /**
     * Ordered canonical history, including the protected compatibility baseline.
     */
    @Transactional
    public List<WorkTimezoneTerm> history(AppUser user) {
        ensureBaseline(user);
        return List.copyOf(
                terms.findByOwnerOrderByEffectiveFromAscIdAsc(user)
        );
    }

    /**
     * Insert or replace one effective term.
     *
     * The returned window tells reconciliation exactly which historical range
     * can change: [effectiveFrom, nextEffectiveFrom).
     */
    @Transactional
    public ChangeWindow upsert(
            AppUser user,
            LocalDateTime effectiveFrom,
            String timezone
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (effectiveFrom == null) {
            throw ApiException.badRequest(
                    "Нужно указать момент вступления часового пояса"
            );
        }

        ensureBaseline(user);

        String canonical = validatedTimezone(timezone);

        String previousTimezone = terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        effectiveFrom
                )
                .map(WorkTimezoneTerm::getTimezoneId)
                .map(this::safePersistedZone)
                .map(ZoneId::getId)
                .orElseGet(() -> safePersistedZone(
                        user.getWorkTimezone()
                ).getId());

        LocalDateTime nextEffectiveFrom = terms
                .findFirstByOwnerAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(
                        user,
                        effectiveFrom
                )
                .map(WorkTimezoneTerm::getEffectiveFrom)
                .orElse(null);

        WorkTimezoneTerm term = terms
                .findByOwnerAndEffectiveFrom(
                        user,
                        effectiveFrom
                )
                .orElseGet(() -> new WorkTimezoneTerm(
                        user,
                        effectiveFrom,
                        canonical
                ));

        term.setTimezoneId(canonical);
        terms.saveAndFlush(term);

        return new ChangeWindow(
                effectiveFrom,
                nextEffectiveFrom,
                previousTimezone,
                canonical
        );
    }

    /**
     * Application-level safety for accounts created after the Flyway migration.
     * Existing accounts already receive the same baseline from V53.
     */
    private void ensureBaseline(AppUser user) {
        if (terms.findByOwnerAndEffectiveFrom(
                user,
                BASELINE_EFFECTIVE_FROM
        ).isPresent()) {
            return;
        }

        WorkTimezoneTerm baseline = new WorkTimezoneTerm(
                user,
                BASELINE_EFFECTIVE_FROM,
                safePersistedZone(user.getWorkTimezone()).getId()
        );

        terms.saveAndFlush(baseline);
    }

    private String validatedTimezone(String raw) {
        String value = raw == null ? "" : raw.trim();

        if (value.isBlank() || value.length() > 80) {
            throw ApiException.badRequest(
                    "Часовой пояс должен быть IANA-идентификатором, например Europe/Moscow"
            );
        }

        try {
            return ZoneId.of(value).getId();
        } catch (DateTimeException ex) {
            throw ApiException.badRequest(
                    "Неизвестный часовой пояс: " + value
            );
        }
    }

    /**
     * Persisted legacy/corrupt profile values fail safe exactly like the
     * existing UserTimeService compatibility boundary.
     */
    private ZoneId safePersistedZone(String raw) {
        try {
            return ZoneId.of(
                    raw == null || raw.isBlank()
                            ? UserTimeService.FALLBACK_ZONE.getId()
                            : raw.trim()
            );
        } catch (DateTimeException ex) {
            return UserTimeService.FALLBACK_ZONE;
        }
    }

    public record ChangeWindow(
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveToExclusive,
            String previousTimezone,
            String timezone
    ) {}
}
