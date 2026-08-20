package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.service.PlannedWorkDayAllocationService.NetWorkSegment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Pure temporal relation kernel between dated planned work and factual work.
 *
 * This layer answers only where plan and fact overlap on the clock:
 * PLANNED_AND_WORKED, WORKED_OUTSIDE_PLAN, PLANNED_NOT_WORKED.
 *
 * It does not decide REGULAR/OVERTIME, pay dimensions, pricing, settlement,
 * Time Bank effects or Payroll money.
 */
@Service
public class PlannedActualWorkRelationEngine {

    public List<RelationSlice> compareDay(
            LocalDate date,
            List<NetWorkSegment> plannedSegments,
            List<ActualWorkDayAllocationService.NetWorkSegment> actualSegments
    ) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "Plan/fact relation requires source date"
            );
        }

        List<NetWorkSegment> planned =
                sanitizePlanned(
                        date,
                        plannedSegments
                );

        List<ActualWorkDayAllocationService.NetWorkSegment> actual =
                sanitizeActual(
                        date,
                        actualSegments
                );

        if (planned.isEmpty()
                && actual.isEmpty()) {
            return List.of();
        }

        /*
         * Planned segments always have absolute identity.
         * If every factual segment is also exact, compare on Instant so DST
         * elapsed minutes stay authoritative. If any legacy factual segment
         * lacks absolute identity, preserve legacy local-wall-clock semantics
         * for the whole comparison instead of inventing historical instants.
         */
        boolean exact =
                actual.stream()
                        .allMatch(
                                ActualWorkDayAllocationService.NetWorkSegment::exact
                        );

        return exact
                ? compareExact(planned, actual)
                : compareLocal(planned, actual);
    }

    private List<NetWorkSegment> sanitizePlanned(
            LocalDate date,
            List<NetWorkSegment> segments
    ) {
        if (segments == null
                || segments.isEmpty()) {
            return List.of();
        }

        List<NetWorkSegment> result =
                segments.stream()
                        .filter(Objects::nonNull)
                        .sorted(
                                Comparator.comparing(
                                        NetWorkSegment::startInstant
                                )
                        )
                        .toList();

        for (NetWorkSegment segment : result) {
            if (!date.equals(
                    segment.sourceDate()
            )) {
                throw new IllegalArgumentException(
                        "Planned relation segment belongs to another source date"
                );
            }
        }

        return result;
    }

    private List<ActualWorkDayAllocationService.NetWorkSegment> sanitizeActual(
            LocalDate date,
            List<ActualWorkDayAllocationService.NetWorkSegment> segments
    ) {
        if (segments == null
                || segments.isEmpty()) {
            return List.of();
        }

        List<ActualWorkDayAllocationService.NetWorkSegment> result =
                segments.stream()
                        .filter(Objects::nonNull)
                        .sorted(
                                Comparator
                                        .comparing(
                                                ActualWorkDayAllocationService.NetWorkSegment::start
                                        )
                                        .thenComparing(
                                                ActualWorkDayAllocationService.NetWorkSegment::end
                                        )
                        )
                        .toList();

        for (ActualWorkDayAllocationService.NetWorkSegment segment : result) {
            if (!date.equals(
                    segment.start()
                            .toLocalDate()
            )) {
                throw new IllegalArgumentException(
                        "Actual relation segment belongs to another source date"
                );
            }
        }

        return result;
    }

    private List<RelationSlice> compareExact(
            List<NetWorkSegment> planned,
            List<ActualWorkDayAllocationService.NetWorkSegment> actual
    ) {
        TreeSet<Instant> boundaries =
                new TreeSet<>();

        for (NetWorkSegment segment : planned) {
            boundaries.add(
                    segment.startInstant()
            );
            boundaries.add(
                    segment.endInstant()
            );
        }

        for (ActualWorkDayAllocationService.NetWorkSegment segment : actual) {
            boundaries.add(
                    segment.startInstant()
            );
            boundaries.add(
                    segment.endInstant()
            );
        }

        List<Instant> ordered =
                List.copyOf(boundaries);

        List<RelationSlice> raw =
                new ArrayList<>();

        for (int i = 0;
             i + 1 < ordered.size();
             i++) {
            Instant start =
                    ordered.get(i);

            Instant end =
                    ordered.get(i + 1);

            if (!end.isAfter(start)) {
                continue;
            }

            NetWorkSegment plannedCover =
                    firstPlannedCover(
                            planned,
                            start,
                            end
                    );

            ActualWorkDayAllocationService.NetWorkSegment actualCover =
                    firstActualCover(
                            actual,
                            start,
                            end
                    );

            WorkPlanRelation relation =
                    relation(
                            plannedCover != null,
                            actualCover != null
                    );

            if (relation == null) {
                continue;
            }

            String sourceTimezone =
                    actualCover != null
                            ? actualCover.sourceTimezone()
                            : plannedCover.sourceTimezone();

            ZoneId zone =
                    ZoneId.of(
                            sourceTimezone
                    );

            raw.add(
                    new RelationSlice(
                            start.atZone(zone)
                                    .toLocalDateTime(),
                            end.atZone(zone)
                                    .toLocalDateTime(),
                            start,
                            end,
                            sourceTimezone,
                            relation
                    )
            );
        }

        return merge(raw);
    }

    private List<RelationSlice> compareLocal(
            List<NetWorkSegment> planned,
            List<ActualWorkDayAllocationService.NetWorkSegment> actual
    ) {
        TreeSet<LocalDateTime> boundaries =
                new TreeSet<>();

        for (NetWorkSegment segment : planned) {
            boundaries.add(
                    segment.start()
            );
            boundaries.add(
                    segment.end()
            );
        }

        for (ActualWorkDayAllocationService.NetWorkSegment segment : actual) {
            boundaries.add(
                    segment.start()
            );
            boundaries.add(
                    segment.end()
            );
        }

        List<LocalDateTime> ordered =
                List.copyOf(boundaries);

        List<RelationSlice> raw =
                new ArrayList<>();

        for (int i = 0;
             i + 1 < ordered.size();
             i++) {
            LocalDateTime start =
                    ordered.get(i);

            LocalDateTime end =
                    ordered.get(i + 1);

            if (!end.isAfter(start)) {
                continue;
            }

            boolean plannedCover =
                    planned.stream()
                            .anyMatch(segment ->
                                    coversLocal(
                                            segment.start(),
                                            segment.end(),
                                            start,
                                            end
                                    )
                            );

            boolean actualCover =
                    actual.stream()
                            .anyMatch(segment ->
                                    coversLocal(
                                            segment.start(),
                                            segment.end(),
                                            start,
                                            end
                                    )
                            );

            WorkPlanRelation relation =
                    relation(
                            plannedCover,
                            actualCover
                    );

            if (relation == null) {
                continue;
            }

            raw.add(
                    new RelationSlice(
                            start,
                            end,
                            null,
                            null,
                            null,
                            relation
                    )
            );
        }

        return merge(raw);
    }

    private NetWorkSegment firstPlannedCover(
            List<NetWorkSegment> segments,
            Instant start,
            Instant end
    ) {
        return segments.stream()
                .filter(segment ->
                        coversExact(
                                segment.startInstant(),
                                segment.endInstant(),
                                start,
                                end
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private ActualWorkDayAllocationService.NetWorkSegment firstActualCover(
            List<ActualWorkDayAllocationService.NetWorkSegment> segments,
            Instant start,
            Instant end
    ) {
        return segments.stream()
                .filter(segment ->
                        coversExact(
                                segment.startInstant(),
                                segment.endInstant(),
                                start,
                                end
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private boolean coversExact(
            Instant segmentStart,
            Instant segmentEnd,
            Instant start,
            Instant end
    ) {
        return !segmentStart.isAfter(start)
                && !segmentEnd.isBefore(end);
    }

    private boolean coversLocal(
            LocalDateTime segmentStart,
            LocalDateTime segmentEnd,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return !segmentStart.isAfter(start)
                && !segmentEnd.isBefore(end);
    }

    private WorkPlanRelation relation(
            boolean planned,
            boolean actual
    ) {
        if (planned
                && actual) {
            return WorkPlanRelation.PLANNED_AND_WORKED;
        }

        if (actual) {
            return WorkPlanRelation.WORKED_OUTSIDE_PLAN;
        }

        if (planned) {
            return WorkPlanRelation.PLANNED_NOT_WORKED;
        }

        return null;
    }

    private List<RelationSlice> merge(
            List<RelationSlice> raw
    ) {
        if (raw.isEmpty()) {
            return List.of();
        }

        List<RelationSlice> result =
                new ArrayList<>();

        for (RelationSlice current : raw) {
            if (result.isEmpty()) {
                result.add(current);
                continue;
            }

            int lastIndex =
                    result.size() - 1;

            RelationSlice previous =
                    result.get(lastIndex);

            if (!canMerge(
                    previous,
                    current
            )) {
                result.add(current);
                continue;
            }

            result.set(
                    lastIndex,
                    merge(
                            previous,
                            current
                    )
            );
        }

        return List.copyOf(result);
    }

    private boolean canMerge(
            RelationSlice left,
            RelationSlice right
    ) {
        if (left.relation()
                != right.relation()) {
            return false;
        }

        if (left.exact()
                != right.exact()) {
            return false;
        }

        if (left.exact()) {
            return left.endInstant()
                    .equals(
                            right.startInstant()
                    )
                    && left.sourceTimezone()
                    .equals(
                            right.sourceTimezone()
                    );
        }

        return left.end()
                .equals(
                        right.start()
                );
    }

    private RelationSlice merge(
            RelationSlice left,
            RelationSlice right
    ) {
        if (left.exact()) {
            return new RelationSlice(
                    left.start(),
                    right.end(),
                    left.startInstant(),
                    right.endInstant(),
                    left.sourceTimezone(),
                    left.relation()
            );
        }

        return new RelationSlice(
                left.start(),
                right.end(),
                null,
                null,
                null,
                left.relation()
        );
    }

    public enum WorkPlanRelation {
        PLANNED_AND_WORKED,
        WORKED_OUTSIDE_PLAN,
        PLANNED_NOT_WORKED
    }

    public record RelationSlice(
            LocalDateTime start,
            LocalDateTime end,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            WorkPlanRelation relation
    ) {
        public RelationSlice {
            if (start == null
                    || end == null
                    || relation == null) {
                throw new IllegalArgumentException(
                        "Plan/fact relation slice requires local boundaries and relation"
                );
            }

            boolean hasAnyAbsoluteField =
                    startInstant != null
                            || endInstant != null
                            || sourceTimezone != null;

            if (hasAnyAbsoluteField) {
                if (startInstant == null
                        || endInstant == null
                        || sourceTimezone == null
                        || sourceTimezone.isBlank()
                        || !endInstant.isAfter(
                        startInstant
                )) {
                    throw new IllegalArgumentException(
                            "Exact plan/fact relation slice requires complete positive absolute identity"
                    );
                }

                ZoneId.of(
                        sourceTimezone
                );
            } else if (!end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "Legacy plan/fact relation slice must have positive local duration"
                );
            }
        }

        public boolean exact() {
            return startInstant != null
                    && endInstant != null
                    && sourceTimezone != null;
        }

        public int minutes() {
            return exact()
                    ? Math.toIntExact(
                    Duration.between(
                            startInstant,
                            endInstant
                    ).toMinutes()
            )
                    : Math.toIntExact(
                    Duration.between(
                            start,
                            end
                    ).toMinutes()
            );
        }
    }
}
