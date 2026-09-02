package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical pure authority for explicit unpaid work-break windows.
 *
 * <p>This class deliberately does not reinterpret legacy scalar break-minute
 * totals. Existing legacy rows retain their historical early-total semantics
 * until a user supplies explicit break evidence.</p>
 *
 * <p>Template windows are expressed as source-wall-clock offsets from the
 * shift start. Once a dated shift is resolved, every break receives immutable
 * absolute identity and can be subtracted before calendar/night/holiday/pay
 * classification.</p>
 */
@Service
public class WorkBreakWindowAuthorityService {
    private final UserTimeService userTime;

    public WorkBreakWindowAuthorityService(UserTimeService userTime) {
        this.userTime = userTime;
    }

    public List<ResolvedBreakWindow> resolve(
            Instant shiftStart,
            Instant shiftEnd,
            LocalDateTime sourceShiftStart,
            String sourceTimezone,
            List<TemplateBreakWindow> windows
    ) {
        requirePositiveShift(shiftStart, shiftEnd);
        if (sourceShiftStart == null) {
            throw new IllegalArgumentException("Source shift start is required");
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(sourceTimezone);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Valid source timezone is required", ex);
        }

        List<TemplateBreakWindow> safe =
                windows == null ? List.of() : List.copyOf(windows);

        Set<Integer> positions = new HashSet<>();
        List<ResolvedBreakWindow> resolved = new ArrayList<>();

        for (TemplateBreakWindow window : safe) {
            if (window == null) {
                throw new IllegalArgumentException("Break window cannot be null");
            }
            if (!positions.add(window.position())) {
                throw new IllegalArgumentException("Break window positions must be unique");
            }

            LocalDateTime configuredStart =
                    sourceShiftStart.plusMinutes(window.startOffsetMinutes());
            LocalDateTime configuredEnd =
                    configuredStart.plusMinutes(window.durationMinutes());

            Instant start =
                    userTime.resolveLocalDateTime(configuredStart, zone).toInstant();
            Instant end =
                    userTime.resolveLocalDateTime(configuredEnd, zone).toInstant();

            if (!end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "Resolved break window must have positive elapsed duration"
                );
            }
            if (start.isBefore(shiftStart) || end.isAfter(shiftEnd)) {
                throw new IllegalArgumentException(
                        "Break window must stay inside the dated shift occurrence"
                );
            }

            resolved.add(new ResolvedBreakWindow(
                    window.position(),
                    start.atZone(zone).toLocalDateTime(),
                    end.atZone(zone).toLocalDateTime(),
                    start,
                    end,
                    zone.getId(),
                    window.startOffsetMinutes(),
                    window.durationMinutes()
            ));
        }

        resolved.sort(
                Comparator.comparing(ResolvedBreakWindow::startInstant)
                        .thenComparingInt(ResolvedBreakWindow::position)
        );

        Instant previousEnd = null;
        for (ResolvedBreakWindow window : resolved) {
            if (previousEnd != null && window.startInstant().isBefore(previousEnd)) {
                throw new IllegalArgumentException(
                        "Explicit break windows must not overlap"
                );
            }
            previousEnd = window.endInstant();
        }

        return List.copyOf(resolved);
    }

    public List<PaidWorkInterval> subtract(
            Instant shiftStart,
            Instant shiftEnd,
            List<ResolvedBreakWindow> breaks
    ) {
        requirePositiveShift(shiftStart, shiftEnd);

        List<ResolvedBreakWindow> safe =
                breaks == null ? List.of() : breaks.stream()
                        .sorted(
                                Comparator.comparing(ResolvedBreakWindow::startInstant)
                                        .thenComparingInt(ResolvedBreakWindow::position)
                        )
                        .toList();

        List<PaidWorkInterval> paid = new ArrayList<>();
        Instant cursor = shiftStart;

        for (ResolvedBreakWindow window : safe) {
            if (window.startInstant().isBefore(shiftStart)
                    || window.endInstant().isAfter(shiftEnd)) {
                throw new IllegalArgumentException(
                        "Resolved break window is outside the work interval"
                );
            }
            if (window.startInstant().isBefore(cursor)) {
                throw new IllegalArgumentException(
                        "Resolved break windows overlap or are out of order"
                );
            }

            if (window.startInstant().isAfter(cursor)) {
                paid.add(new PaidWorkInterval(cursor, window.startInstant()));
            }
            cursor = window.endInstant();
        }

        if (cursor.isBefore(shiftEnd)) {
            paid.add(new PaidWorkInterval(cursor, shiftEnd));
        }

        return List.copyOf(paid);
    }

    public long paidMinutes(
            Instant shiftStart,
            Instant shiftEnd,
            List<ResolvedBreakWindow> breaks
    ) {
        return subtract(shiftStart, shiftEnd, breaks).stream()
                .mapToLong(PaidWorkInterval::minutes)
                .sum();
    }

    private static void requirePositiveShift(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "Dated shift occurrence requires a positive absolute interval"
            );
        }
    }

    public record TemplateBreakWindow(
            int position,
            int startOffsetMinutes,
            int durationMinutes
    ) {
        public TemplateBreakWindow {
            if (position < 0) {
                throw new IllegalArgumentException("Break position cannot be negative");
            }
            if (startOffsetMinutes < 0 || startOffsetMinutes >= 1440) {
                throw new IllegalArgumentException(
                        "Break start offset must be from 0 to 1439 minutes"
                );
            }
            if (durationMinutes <= 0 || durationMinutes > 1440) {
                throw new IllegalArgumentException(
                        "Break duration must be from 1 to 1440 minutes"
                );
            }
            if ((long) startOffsetMinutes + durationMinutes > 1440L) {
                throw new IllegalArgumentException(
                        "Break template must fit inside one <=24h shift wall-clock span"
                );
            }
        }
    }

    public record ResolvedBreakWindow(
            int position,
            LocalDateTime sourceStart,
            LocalDateTime sourceEnd,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            int configuredStartOffsetMinutes,
            int configuredDurationMinutes
    ) {
        public ResolvedBreakWindow {
            if (position < 0
                    || sourceStart == null
                    || sourceEnd == null
                    || startInstant == null
                    || endInstant == null
                    || sourceTimezone == null
                    || sourceTimezone.isBlank()
                    || !endInstant.isAfter(startInstant)) {
                throw new IllegalArgumentException(
                        "Resolved break window requires complete positive identity"
                );
            }
        }

        public long elapsedMinutes() {
            return Duration.between(startInstant, endInstant).toMinutes();
        }
    }

    public record PaidWorkInterval(
            Instant startInstant,
            Instant endInstant
    ) {
        public PaidWorkInterval {
            if (startInstant == null
                    || endInstant == null
                    || !endInstant.isAfter(startInstant)) {
                throw new IllegalArgumentException(
                        "Paid work interval requires positive absolute identity"
                );
            }
        }

        public long minutes() {
            return Duration.between(startInstant, endInstant).toMinutes();
        }
    }
}
