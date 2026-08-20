package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Converts one local Actual Work fact into its historical absolute identity
 * using the effective Work Timezone context.
 */
@Service
public class ActualWorkIdentityService {

    private final WorkTimezoneHistoryService timezoneHistory;
    private final UserTimeService userTime;

    public ActualWorkIdentityService(
            WorkTimezoneHistoryService timezoneHistory,
            UserTimeService userTime
    ) {
        this.timezoneHistory = timezoneHistory;
        this.userTime = userTime;
    }

    public Identity resolve(
            AppUser user,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (user == null
                || startDate == null
                || endDate == null
                || startTime == null
                || endTime == null) {
            throw new IllegalArgumentException(
                    "Complete Actual Work shape is required"
            );
        }

        LocalDateTime localStart = startDate.atTime(startTime);
        LocalDateTime localEnd = endDate.atTime(endTime);

        if (!localEnd.isAfter(localStart)) {
            throw ApiException.badRequest(
                    "Фактический интервал должен иметь положительную длительность"
            );
        }

        /*
         * One Actual Work row must have one source timezone.
         *
         * Example:
         * 13:00 ---- work ---- 17:00
         *             ^
         *       timezone changes 14:00
         *
         * This must be split into two factual intervals instead of silently
         * manufacturing one mixed-zone fact.
         */
        timezoneHistory
                .nextEffectiveFromAfter(user, localStart)
                .filter(boundary -> boundary.isBefore(localEnd))
                .ifPresent(boundary -> {
                    throw ApiException.conflict(
                            "WORK_CONTEXT_BOUNDARY_INSIDE_ACTUAL_WORK",
                            "Фактический интервал пересекает изменение рабочего "
                                    + "часового пояса " + boundary
                                    + ". Раздели факт работы по границе контекста."
                    );
                });

        ZoneId sourceZone = timezoneHistory.zoneAt(
                user,
                localStart
        );

        Instant startInstant = userTime
                .resolveLocalDateTime(localStart, sourceZone)
                .toInstant();

        Instant endInstant = userTime
                .resolveLocalDateTime(localEnd, sourceZone)
                .toInstant();

        long elapsedMinutes = Duration
                .between(startInstant, endInstant)
                .toMinutes();

        if (elapsedMinutes <= 0) {
            throw ApiException.badRequest(
                    "Фактический интервал должен иметь положительную длительность"
            );
        }

        if (elapsedMinutes > 48L * 60L) {
            throw ApiException.badRequest(
                    "Фактический интервал не может быть длиннее 48 часов"
            );
        }

        return new Identity(
                sourceZone.getId(),
                startInstant,
                endInstant,
                Math.toIntExact(elapsedMinutes)
        );
    }

    public record Identity(
            String sourceTimezone,
            Instant startInstant,
            Instant endInstant,
            int elapsedMinutes
    ) {}
}
