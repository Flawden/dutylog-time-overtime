package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.PlannedActualWorkRelationEngine.RelationSlice;
import ru.daniil.shifts.service.PlannedActualWorkRelationEngine.WorkPlanRelation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlannedActualWorkRelationEngineTest {

    private final PlannedActualWorkRelationEngine engine =
            new PlannedActualWorkRelationEngine();

    @Test
    void exactMatchIsEntirelyPlannedAndWorked() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T17:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T08:00",
                                        "2026-08-18T17:00"
                                )
                        )
                );

        assertEquals(1, slices.size());

        assertSlice(
                slices.get(0),
                WorkPlanRelation.PLANNED_AND_WORKED,
                "2026-08-18T08:00",
                "2026-08-18T17:00",
                540
        );

        assertInvariants(slices, 540, 540);
    }

    @Test
    void actualBeforeAndAfterPlanProducesOutsidePlanOnBothSides() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T17:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T06:00",
                                        "2026-08-18T19:00"
                                )
                        )
                );

        assertEquals(3, slices.size());

        assertSlice(
                slices.get(0),
                WorkPlanRelation.WORKED_OUTSIDE_PLAN,
                "2026-08-18T06:00",
                "2026-08-18T08:00",
                120
        );

        assertSlice(
                slices.get(1),
                WorkPlanRelation.PLANNED_AND_WORKED,
                "2026-08-18T08:00",
                "2026-08-18T17:00",
                540
        );

        assertSlice(
                slices.get(2),
                WorkPlanRelation.WORKED_OUTSIDE_PLAN,
                "2026-08-18T17:00",
                "2026-08-18T19:00",
                120
        );

        assertInvariants(slices, 540, 780);
    }

    @Test
    void partialActualLeavesPlannedNotWorkedOnBothSides() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T17:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T10:00",
                                        "2026-08-18T15:00"
                                )
                        )
                );

        assertEquals(3, slices.size());

        assertSlice(
                slices.get(0),
                WorkPlanRelation.PLANNED_NOT_WORKED,
                "2026-08-18T08:00",
                "2026-08-18T10:00",
                120
        );

        assertSlice(
                slices.get(1),
                WorkPlanRelation.PLANNED_AND_WORKED,
                "2026-08-18T10:00",
                "2026-08-18T15:00",
                300
        );

        assertSlice(
                slices.get(2),
                WorkPlanRelation.PLANNED_NOT_WORKED,
                "2026-08-18T15:00",
                "2026-08-18T17:00",
                120
        );

        assertInvariants(slices, 540, 300);
    }

    @Test
    void disjointPlanAndActualNeverInventOverlap() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T12:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T15:00",
                                        "2026-08-18T18:00"
                                )
                        )
                );

        assertEquals(2, slices.size());

        assertSlice(
                slices.get(0),
                WorkPlanRelation.PLANNED_NOT_WORKED,
                "2026-08-18T08:00",
                "2026-08-18T12:00",
                240
        );

        assertSlice(
                slices.get(1),
                WorkPlanRelation.WORKED_OUTSIDE_PLAN,
                "2026-08-18T15:00",
                "2026-08-18T18:00",
                180
        );

        assertInvariants(slices, 240, 180);
    }

    @Test
    void touchingBoundariesDoNotOverlap() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T12:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T12:00",
                                        "2026-08-18T14:00"
                                )
                        )
                );

        assertEquals(2, slices.size());

        assertEquals(
                0,
                minutes(
                        slices,
                        WorkPlanRelation.PLANNED_AND_WORKED
                )
        );

        assertInvariants(slices, 240, 120);
    }

    @Test
    void fragmentedPlanAndActualPreserveUnionAndBothMarginals() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T12:00"
                                ),
                                planned(
                                        "2026-08-18T13:00",
                                        "2026-08-18T17:00"
                                )
                        ),
                        List.of(
                                actual(
                                        "2026-08-18T07:00",
                                        "2026-08-18T09:00"
                                ),
                                actual(
                                        "2026-08-18T10:00",
                                        "2026-08-18T14:00"
                                ),
                                actual(
                                        "2026-08-18T16:00",
                                        "2026-08-18T18:00"
                                )
                        )
                );

        assertEquals(
                300,
                minutes(
                        slices,
                        WorkPlanRelation.PLANNED_AND_WORKED
                )
        );

        assertEquals(
                180,
                minutes(
                        slices,
                        WorkPlanRelation.PLANNED_NOT_WORKED
                )
        );

        assertEquals(
                180,
                minutes(
                        slices,
                        WorkPlanRelation.WORKED_OUTSIDE_PLAN
                )
        );

        assertInvariants(slices, 480, 480);
    }

    @Test
    void springForwardUsesAbsoluteElapsedMinutes() {
        ZoneId berlin =
                ZoneId.of(
                        "Europe/Berlin"
                );

        var planned =
                planned(
                        berlin,
                        "2026-03-29T00:00",
                        "2026-03-29T08:00"
                );

        var actual =
                actual(
                        berlin,
                        "2026-03-29T00:00",
                        "2026-03-29T08:00"
                );

        var slices =
                engine.compareDay(
                        LocalDate.of(
                                2026,
                                3,
                                29
                        ),
                        List.of(planned),
                        List.of(actual)
                );

        assertEquals(1, slices.size());
        assertEquals(420, slices.get(0).minutes());
        assertTrue(slices.get(0).exact());

        assertInvariants(slices, 420, 420);
    }

    @Test
    void legacyActualFallsBackToLocalWallClockWithoutInventingAbsoluteIdentity() {
        var slices =
                engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-18T08:00",
                                        "2026-08-18T17:00"
                                )
                        ),
                        List.of(
                                legacyActual(
                                        "2026-08-18T10:00",
                                        "2026-08-18T15:00"
                                )
                        )
                );

        assertTrue(
                slices.stream()
                        .noneMatch(
                                RelationSlice::exact
                        )
        );

        assertInvariants(slices, 540, 300);
    }

    @Test
    void rejectsSegmentsFromAnotherSourceDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.compareDay(
                        date(),
                        List.of(
                                planned(
                                        "2026-08-19T08:00",
                                        "2026-08-19T09:00"
                                )
                        ),
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.compareDay(
                        date(),
                        List.of(),
                        List.of(
                                actual(
                                        "2026-08-19T08:00",
                                        "2026-08-19T09:00"
                                )
                        )
                )
        );
    }

    private void assertInvariants(
            List<RelationSlice> slices,
            int plannedMinutes,
            int actualMinutes
    ) {
        int plannedAndWorked =
                minutes(
                        slices,
                        WorkPlanRelation.PLANNED_AND_WORKED
                );

        int plannedNotWorked =
                minutes(
                        slices,
                        WorkPlanRelation.PLANNED_NOT_WORKED
                );

        int workedOutsidePlan =
                minutes(
                        slices,
                        WorkPlanRelation.WORKED_OUTSIDE_PLAN
                );

        assertEquals(
                plannedMinutes,
                plannedAndWorked
                        + plannedNotWorked
        );

        assertEquals(
                actualMinutes,
                plannedAndWorked
                        + workedOutsidePlan
        );

        assertEquals(
                plannedMinutes
                        + actualMinutes
                        - plannedAndWorked,
                slices.stream()
                        .mapToInt(
                                RelationSlice::minutes
                        )
                        .sum()
        );
    }

    private int minutes(
            List<RelationSlice> slices,
            WorkPlanRelation relation
    ) {
        return slices.stream()
                .filter(slice ->
                        slice.relation()
                                == relation
                )
                .mapToInt(
                        RelationSlice::minutes
                )
                .sum();
    }

    private void assertSlice(
            RelationSlice slice,
            WorkPlanRelation relation,
            String start,
            String end,
            int minutes
    ) {
        assertEquals(
                relation,
                slice.relation()
        );

        assertEquals(
                LocalDateTime.parse(start),
                slice.start()
        );

        assertEquals(
                LocalDateTime.parse(end),
                slice.end()
        );

        assertEquals(
                minutes,
                slice.minutes()
        );
    }

    private LocalDate date() {
        return LocalDate.of(
                2026,
                8,
                18
        );
    }

    private PlannedWorkDayAllocationService.NetWorkSegment planned(
            String start,
            String end
    ) {
        return planned(
                ZoneId.of("UTC"),
                start,
                end
        );
    }

    private PlannedWorkDayAllocationService.NetWorkSegment planned(
            ZoneId zone,
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(zone);

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(zone);

        return new PlannedWorkDayAllocationService.NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                zone.getId()
        );
    }

    private ActualWorkDayAllocationService.NetWorkSegment actual(
            String start,
            String end
    ) {
        return actual(
                ZoneId.of("UTC"),
                start,
                end
        );
    }

    private ActualWorkDayAllocationService.NetWorkSegment actual(
            ZoneId zone,
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(zone);

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(zone);

        return new ActualWorkDayAllocationService.NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                zone.getId()
        );
    }

    private ActualWorkDayAllocationService.NetWorkSegment legacyActual(
            String start,
            String end
    ) {
        return new ActualWorkDayAllocationService.NetWorkSegment(
                LocalDateTime.parse(start),
                LocalDateTime.parse(end),
                null,
                null,
                null
        );
    }
}
