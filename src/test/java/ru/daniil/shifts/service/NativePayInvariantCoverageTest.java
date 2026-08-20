package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeCreditSlice;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.OvertimeAllocationProvenanceService.AllocationProvenance;
import ru.daniil.shifts.service.OvertimeAllocationProvenanceService.ConsumedProvenancePiece;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationEngine.NightWindow;
import ru.daniil.shifts.service.PayClassificationEngine.SourceWorkSegment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativePayInvariantCoverageTest {

    private final PayClassificationEngine engine =
            new PayClassificationEngine();

    private final LocalDate date =
            LocalDate.of(
                    2026,
                    8,
                    18
            );

    private final NightWindow night =
            new NightWindow(
                    LocalTime.of(22, 0),
                    LocalTime.of(6, 0)
            );

    private final AppUser owner =
            new AppUser(
                    "native-pay-invariant-coverage",
                    "{noop}unused"
            );

    @Test
    void nightWindowRejectsInvalidBoundariesAndHandlesBothWindowShapes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        null,
                        LocalTime.of(6, 0)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(22, 0),
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 0)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(8, 0, 1),
                        LocalTime.of(17, 0)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(
                                8,
                                0,
                                0,
                                1
                        ),
                        LocalTime.of(17, 0)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(8, 0),
                        LocalTime.of(17, 0, 1)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NightWindow(
                        LocalTime.of(8, 0),
                        LocalTime.of(
                                17,
                                0,
                                0,
                                1
                        )
                )
        );

        NightWindow daytime =
                new NightWindow(
                        LocalTime.of(8, 0),
                        LocalTime.of(17, 0)
                );

        assertFalse(
                daytime.contains(
                        LocalTime.of(7, 59)
                )
        );

        assertTrue(
                daytime.contains(
                        LocalTime.of(8, 0)
                )
        );

        assertTrue(
                daytime.contains(
                        LocalTime.of(12, 0)
                )
        );

        assertFalse(
                daytime.contains(
                        LocalTime.of(17, 0)
                )
        );

        assertFalse(
                daytime.contains(
                        LocalTime.of(18, 0)
                )
        );

        assertTrue(
                night.contains(
                        LocalTime.of(22, 0)
                )
        );

        assertTrue(
                night.contains(
                        LocalTime.of(5, 59)
                )
        );

        assertFalse(
                night.contains(
                        LocalTime.of(6, 0)
                )
        );

        assertFalse(
                night.contains(
                        LocalTime.of(12, 0)
                )
        );
    }

    @Test
    void classificationEntryPointsRejectInvalidIdentityAndIgnoreNullLegacyFacts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDay(
                        null,
                        List.of(),
                        0,
                        false,
                        night
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDay(
                        date,
                        List.of(),
                        -1,
                        false,
                        night
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDay(
                        date,
                        List.of(),
                        0,
                        false,
                        null
                )
        );

        assertTrue(
                engine.classifyDay(
                        date,
                        null,
                        0,
                        false,
                        night
                ).isEmpty()
        );

        assertTrue(
                engine.classifyDay(
                        date,
                        java.util.Collections.singletonList(
                                (NetWorkSegment) null
                        ),
                        0,
                        false,
                        night
                ).isEmpty()
        );

        NetWorkSegment legacy =
                legacy(
                        "2026-08-18T08:00",
                        "2026-08-18T09:00"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceWorkSegment(
                        1L,
                        null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDayWithSources(
                        date,
                        java.util.Collections.singletonList(
                                (SourceWorkSegment) null
                        ),
                        60,
                        false,
                        night
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDayWithSources(
                        date,
                        List.of(
                                new SourceWorkSegment(
                                        null,
                                        legacy
                                )
                        ),
                        60,
                        false,
                        night
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDayWithSources(
                        date,
                        List.of(
                                new SourceWorkSegment(
                                        0L,
                                        legacy
                                )
                        ),
                        60,
                        false,
                        night
                )
        );

        NetWorkSegment exact =
                new NetWorkSegment(
                        LocalDateTime.parse(
                                "2026-08-18T08:00"
                        ),
                        LocalDateTime.parse(
                                "2026-08-18T09:00"
                        ),
                        Instant.parse(
                                "2026-08-18T05:00:00Z"
                        ),
                        Instant.parse(
                                "2026-08-18T06:00:00Z"
                        ),
                        "Europe/Moscow"
                );

        assertEquals(
                120,
                total(
                        engine.classifyDay(
                                date,
                                List.of(
                                        exact,
                                        legacy
                                ),
                                120,
                                false,
                                night
                        )
                )
        );

        assertEquals(
                120,
                total(
                        engine.classifyDay(
                                date,
                                List.of(
                                        legacy,
                                        exact
                                ),
                                120,
                                false,
                                night
                        )
                )
        );
    }

    @Test
    void classificationSliceEnforcesPartitionAndTemporalIdentity() {
        LocalDateTime start =
                LocalDateTime.parse(
                        "2026-08-18T08:00"
                );

        LocalDateTime end =
                LocalDateTime.parse(
                        "2026-08-18T09:00"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        null,
                        end,
                        null,
                        null,
                        null,
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        null,
                        null,
                        null,
                        null,
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        0,
                        0,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        -1,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        0,
                        60,
                        true,
                        true
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        0,
                        60,
                        false,
                        false
                )
        );

        Instant instantStart =
                Instant.parse(
                        "2026-08-18T08:00:00Z"
                );

        Instant instantEnd =
                Instant.parse(
                        "2026-08-18T09:00:00Z"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        instantStart,
                        null,
                        null,
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        instantEnd,
                        null,
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        "UTC",
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        instantStart,
                        instantStart,
                        "UTC",
                        0,
                        60,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        instantStart,
                        instantEnd,
                        "UTC",
                        0,
                        30,
                        true,
                        false
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        0,
                        30,
                        true,
                        false
                )
        );

        ClassificationSlice legacy =
                classificationSlice(
                        start,
                        end,
                        null,
                        null,
                        null,
                        0,
                        60,
                        true,
                        false
                );

        assertFalse(
                legacy.exact()
        );

        ClassificationSlice exact =
                classificationSlice(
                        start,
                        end,
                        instantStart,
                        instantEnd,
                        "UTC",
                        0,
                        60,
                        false,
                        true
                );

        assertTrue(
                exact.exact()
        );

        assertTrue(
                exact.overtime()
        );

        assertFalse(
                exact.regular()
        );
    }

    @Test
    void overtimeCreditSliceRejectsInvalidFactualAndCreditIdentity() {
        OvertimeCredit credit =
                systemCredit(date);

        ActualWorkInterval source =
                actual();

        LocalDateTime start =
                LocalDateTime.parse(
                        "2026-08-18T21:00"
                );

        LocalDateTime end =
                LocalDateTime.parse(
                        "2026-08-18T22:00"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        null,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        OvertimeCredit manual =
                systemCredit(date);

        manual.setSourceKind(
                "MANUAL"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        manual,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        null,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        null,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        null,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        null,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        -1,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        0,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        -1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        LocalDateTime.parse(
                                "2026-08-19T21:00"
                        ),
                        LocalDateTime.parse(
                                "2026-08-19T22:00"
                        ),
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        LocalDateTime.parse(
                                "2026-08-18T23:30"
                        ),
                        LocalDateTime.parse(
                                "2026-08-19T00:30"
                        ),
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        OvertimeCredit wrongDateCredit =
                systemCredit(
                        date.plusDays(1)
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        wrongDateCredit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        90,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );
    }

    @Test
    void overtimeCreditSliceSeparatesLegacyAndExactIdentityFailClosed() {
        OvertimeCredit credit =
                systemCredit(date);

        ActualWorkInterval source =
                actual();

        LocalDateTime start =
                LocalDateTime.parse(
                        "2026-08-18T21:00"
                );

        LocalDateTime end =
                LocalDateTime.parse(
                        "2026-08-18T22:00"
                );

        OvertimeCreditSlice legacy =
                slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                );

        assertFalse(
                legacy.exact()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        end,
                        start,
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        30,
                        null,
                        null,
                        null,
                        480
                )
        );

        Instant instantStart =
                Instant.parse(
                        "2026-08-18T18:00:00Z"
                );

        Instant instantEnd =
                Instant.parse(
                        "2026-08-18T19:00:00Z"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        instantStart,
                        null,
                        "Europe/Moscow",
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        instantEnd,
                        "Europe/Moscow",
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        null,
                        null,
                        "Europe/Moscow",
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        instantStart,
                        instantEnd,
                        null,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        instantStart,
                        instantEnd,
                        "   ",
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        instantStart,
                        instantStart,
                        "Europe/Moscow",
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        30,
                        instantStart,
                        instantEnd,
                        "Europe/Moscow",
                        480
                )
        );

        OvertimeCreditSlice exact =
                slice(
                        credit,
                        source,
                        date,
                        start,
                        end,
                        0,
                        60,
                        instantStart,
                        instantEnd,
                        " Europe/Moscow ",
                        480
                );

        assertTrue(
                exact.exact()
        );

        assertEquals(
                "Europe/Moscow",
                exact.getSourceTimezone()
        );
    }

    @Test
    void allocationProvenanceEnforcesCoverageStateMachine() {
        ConsumedProvenancePiece piece =
                legacyPiece();

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        -1,
                        60,
                        false,
                        0,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        0,
                        false,
                        0,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        false,
                        -1,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        false,
                        61,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        false,
                        0,
                        List.of(piece)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        false,
                        1,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        true,
                        59,
                        List.of(piece)
                )
        );

        AllocationProvenance unknown =
                new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        false,
                        0,
                        null
                );

        assertFalse(
                unknown.provenanceKnown()
        );

        assertTrue(
                unknown.pieces().isEmpty()
        );

        AllocationProvenance known =
                new AllocationProvenance(
                        1L,
                        2L,
                        0,
                        60,
                        true,
                        60,
                        List.of(piece)
                );

        assertTrue(
                known.provenanceKnown()
        );

        assertEquals(
                60,
                known.coveredMinutes()
        );
    }

    @Test
    void consumedProvenancePieceRejectsIncompleteAndContradictoryIdentity() {
        LocalDateTime start =
                LocalDateTime.parse(
                        "2026-08-18T21:00"
                );

        LocalDateTime end =
                LocalDateTime.parse(
                        "2026-08-18T22:00"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        -1,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        0,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        null,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        0L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        null,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        null,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        null,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        -1
                )
        );

        Instant instantStart =
                Instant.parse(
                        "2026-08-18T18:00:00Z"
                );

        Instant instantEnd =
                Instant.parse(
                        "2026-08-18T19:00:00Z"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        null,
                        null,
                        false,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        instantEnd,
                        "Europe/Moscow",
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        null,
                        "Europe/Moscow",
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        instantEnd,
                        null,
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        instantEnd,
                        "   ",
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        instantStart,
                        "Europe/Moscow",
                        true,
                        480
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        start,
                        null,
                        null,
                        null,
                        false,
                        480
                )
        );

        ConsumedProvenancePiece legacy =
                piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        null,
                        null,
                        null,
                        false,
                        480
                );

        assertFalse(
                legacy.exact()
        );

        ConsumedProvenancePiece exact =
                piece(
                        0,
                        60,
                        10L,
                        date,
                        start,
                        end,
                        instantStart,
                        instantEnd,
                        "Europe/Moscow",
                        true,
                        480
                );

        assertTrue(
                exact.exact()
        );
    }

    @Test
    void validInvariantObjectsPreserveClassificationAndProvenanceMeaning() {
        ClassificationSlice classification =
                classificationSlice(
                        LocalDateTime.parse(
                                "2026-08-18T22:00"
                        ),
                        LocalDateTime.parse(
                                "2026-08-18T23:00"
                        ),
                        null,
                        null,
                        null,
                        480,
                        60,
                        false,
                        true
                );

        assertEquals(
                480,
                classification.workedOrdinalStartMinutes()
        );

        assertTrue(
                classification.overtime()
        );

        OvertimeCreditSlice creditSlice =
                slice(
                        systemCredit(date),
                        actual(),
                        date,
                        LocalDateTime.parse(
                                "2026-08-18T22:00"
                        ),
                        LocalDateTime.parse(
                                "2026-08-18T23:00"
                        ),
                        0,
                        60,
                        null,
                        null,
                        null,
                        480
                );

        assertEquals(
                480,
                creditSlice.getOvertimeOrdinalStartMinutes()
        );

        ConsumedProvenancePiece consumed =
                legacyPiece();

        assertEquals(
                480,
                consumed.overtimeOrdinalStartMinutes()
        );

        assertEquals(
                60,
                consumed.minutes()
        );
    }

    private ClassificationSlice classificationSlice(
            LocalDateTime start,
            LocalDateTime end,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone,
            int ordinal,
            int minutes,
            boolean regular,
            boolean overtime
    ) {
        return new ClassificationSlice(
                start,
                end,
                startInstant,
                endInstant,
                sourceTimezone,
                10L,
                ordinal,
                minutes,
                regular,
                false,
                false,
                overtime
        );
    }

    private OvertimeCreditSlice slice(
            OvertimeCredit credit,
            ActualWorkInterval source,
            LocalDate sourceDate,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            int offset,
            int minutes,
            Instant instantStart,
            Instant instantEnd,
            String timezone,
            int overtimeOrdinal
    ) {
        return new OvertimeCreditSlice(
                credit,
                offset,
                minutes,
                source,
                sourceDate,
                localStart,
                localEnd,
                instantStart,
                instantEnd,
                timezone,
                false,
                false,
                overtimeOrdinal
        );
    }

    private ConsumedProvenancePiece piece(
            int creditOffset,
            int minutes,
            Long sourceActualId,
            LocalDate sourceDate,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            Instant instantStart,
            Instant instantEnd,
            String timezone,
            boolean exact,
            int overtimeOrdinal
    ) {
        return new ConsumedProvenancePiece(
                creditOffset,
                minutes,
                sourceActualId,
                sourceDate,
                localStart,
                localEnd,
                instantStart,
                instantEnd,
                timezone,
                exact,
                false,
                false,
                overtimeOrdinal
        );
    }

    private ConsumedProvenancePiece legacyPiece() {
        return piece(
                0,
                60,
                10L,
                date,
                LocalDateTime.parse(
                        "2026-08-18T21:00"
                ),
                LocalDateTime.parse(
                        "2026-08-18T22:00"
                ),
                null,
                null,
                null,
                false,
                480
        );
    }

    private ActualWorkInterval actual() {
        ActualWorkInterval value =
                new ActualWorkInterval(
                        owner
                );

        value.setWorkDate(date);
        value.setEndDate(date);
        value.setStartTime(
                LocalTime.of(21, 0)
        );
        value.setEndTime(
                LocalTime.of(23, 0)
        );
        value.setWorkedMinutes(120);
        value.setBreakMinutes(0);

        return value;
    }

    private OvertimeCredit systemCredit(
            LocalDate workDate
    ) {
        OvertimeCredit value =
                new OvertimeCredit(
                        owner,
                        workDate,
                        "Факт дня",
                        2.0,
                        "native pay invariant coverage"
                );

        value.setSourceKind(
                "SYSTEM_ACTUAL_WORK"
        );

        value.setCreditedMinutes(120);
        value.setPlannedHours(8.0);
        value.setCalculated(false);

        return value;
    }

    private NetWorkSegment legacy(
            String start,
            String end
    ) {
        return new NetWorkSegment(
                LocalDateTime.parse(start),
                LocalDateTime.parse(end),
                null,
                null,
                null
        );
    }

    private int total(
            List<ClassificationSlice> slices
    ) {
        return slices.stream()
                .mapToInt(
                        ClassificationSlice::minutes
                )
                .sum();
    }
}
