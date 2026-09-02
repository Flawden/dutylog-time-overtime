package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayEntryShiftBreakWindow;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.model.WorkBreakAuthority;

import java.util.List;

/**
 * Freezes planned break placement into the same dated occurrence that owns the
 * immutable shift absolute identity.
 *
 * Historical legacy rows are never upgraded from scalar minutes to guessed
 * positions. Only a current assignment whose template explicitly proves break
 * windows may receive EXPLICIT_WINDOWS.
 */
@Service
public class PlannedBreakWindowSnapshotService {
    private final WorkBreakWindowAuthorityService breakAuthority;

    public PlannedBreakWindowSnapshotService(
            WorkBreakWindowAuthorityService breakAuthority
    ) {
        this.breakAuthority = breakAuthority;
    }

    public void captureCurrentAssignment(
            DayEntry entry,
            WorkIntervalService.ResolvedWorkInterval interval
    ) {
        ShiftType shift = requireShift(entry);
        if (shift.getBreakAuthority() != WorkBreakAuthority.EXPLICIT_WINDOWS) {
            captureLegacyEvidence(entry, interval);
            return;
        }

        List<WorkBreakWindowAuthorityService.TemplateBreakWindow> templates =
                shift.getBreakWindows().stream()
                        .map(window ->
                                new WorkBreakWindowAuthorityService.TemplateBreakWindow(
                                        window.getPosition(),
                                        window.getStartOffsetMinutes(),
                                        window.getDurationMinutes()
                                )
                        )
                        .toList();

        List<WorkBreakWindowAuthorityService.ResolvedBreakWindow> resolved =
                breakAuthority.resolve(
                        interval.startInstant(),
                        interval.endInstant(),
                        interval.localStart(),
                        interval.workTimezone(),
                        templates
                );

        long paidMinutes = breakAuthority.paidMinutes(
                interval.startInstant(),
                interval.endInstant(),
                resolved
        );
        long breakMinutes = interval.elapsedMinutes() - paidMinutes;
        if (breakMinutes < 0 || breakMinutes > Integer.MAX_VALUE) {
            throw new IllegalStateException("Explicit break total is outside supported range");
        }

        List<DayEntryShiftBreakWindow> snapshots =
                resolved.stream()
                        .map(window ->
                                new DayEntryShiftBreakWindow(
                                        entry,
                                        window.position(),
                                        window.sourceStart(),
                                        window.sourceEnd(),
                                        window.startInstant(),
                                        window.endInstant(),
                                        window.sourceTimezone()
                                )
                        )
                        .toList();

        entry.captureExplicitShiftOccurrence(
                interval.startInstant(),
                interval.endInstant(),
                interval.workTimezone(),
                interval.workDate(),
                interval.localStart().toLocalTime(),
                interval.localEnd().toLocalTime(),
                Math.toIntExact(breakMinutes),
                paidMinutes,
                snapshots
        );
    }

    /**
     * Calendar projection of paid work geometry from frozen planned evidence.
     *
     * Legacy scalar break totals do not identify a position and are therefore
     * never converted into guessed display segments.
     */
    public CalendarPaidSegmentProjection calendarPaidSegments(DayEntry entry) {
        if (entry == null || !entry.hasShiftOccurrenceSnapshot()) {
            return new CalendarPaidSegmentProjection(false, List.of());
        }

        List<WorkBreakWindowAuthorityService.PaidWorkInterval> paid;
        if (entry.getShiftBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS) {
            List<WorkBreakWindowAuthorityService.AbsoluteBreakWindow> breaks =
                    entry.getShiftBreakWindows().stream()
                            .map(window ->
                                    new WorkBreakWindowAuthorityService.AbsoluteBreakWindow(
                                            window.getPosition(),
                                            window.getStartInstant(),
                                            window.getEndInstant()
                                    )
                            )
                            .toList();
            paid = breakAuthority.subtractAbsolute(
                    entry.getShiftStartInstant(),
                    entry.getShiftEndInstant(),
                    breaks
            );
        } else if (entry.getShiftBreakMinutes() == 0) {
            paid = breakAuthority.subtractAbsolute(
                    entry.getShiftStartInstant(),
                    entry.getShiftEndInstant(),
                    List.of()
            );
        } else {
            return new CalendarPaidSegmentProjection(false, List.of());
        }

        long projectedMinutes = paid.stream()
                .mapToLong(WorkBreakWindowAuthorityService.PaidWorkInterval::minutes)
                .sum();
        if (projectedMinutes != entry.getShiftNetMinutes()) {
            throw new IllegalStateException(
                    "Frozen calendar paid segments disagree with stored shift net minutes"
            );
        }
        return new CalendarPaidSegmentProjection(true, paid);
    }

    public record CalendarPaidSegmentProjection(
            boolean precise,
            List<WorkBreakWindowAuthorityService.PaidWorkInterval> intervals
    ) {
        public CalendarPaidSegmentProjection {
            intervals = intervals == null ? List.of() : List.copyOf(intervals);
        }
    }

    public void captureLegacyEvidence(
            DayEntry entry,
            WorkIntervalService.ResolvedWorkInterval interval
    ) {
        requireShift(entry);
        entry.captureShiftOccurrence(
                interval.startInstant(),
                interval.endInstant(),
                interval.workTimezone(),
                interval.workDate(),
                interval.localStart().toLocalTime(),
                interval.localEnd().toLocalTime(),
                interval.breakMinutes(),
                interval.netMinutes()
        );
    }

    private static ShiftType requireShift(DayEntry entry) {
        if (entry == null || entry.getShiftType() == null) {
            throw new IllegalArgumentException("Dated planned shift is required");
        }
        return entry.getShiftType();
    }
}
