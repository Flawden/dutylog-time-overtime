package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationService.DayClassification;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayPricingPolicyService.ResolvedPricingPolicy;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdinaryPremiumDeepSourceIdentityTest {

    private final AppUser user =
            new AppUser(
                    "ordinary-deep-source",
                    "{noop}unused"
            );

    @Test
    void explicitSourceRetainsActualWorkAndExactClockEvidence() {
        ActualWorkIntervalRepository actualWork =
                mock(ActualWorkIntervalRepository.class);

        ActualWorkDayAllocationService actualAllocation =
                mock(ActualWorkDayAllocationService.class);

        DayEntryRepository days =
                mock(DayEntryRepository.class);

        VacationPlannerService vacationPlanner =
                mock(VacationPlannerService.class);

        ProductionCalendarService productionCalendar =
                mock(ProductionCalendarService.class);

        PlannedWorkDayAllocationService plannedAllocation =
                mock(PlannedWorkDayAllocationService.class);

        PayClassificationService classification =
                mock(PayClassificationService.class);

        OrdinaryWorkPremiumSourceService service =
                new OrdinaryWorkPremiumSourceService(
                        actualWork,
                        actualAllocation,
                        days,
                        vacationPlanner,
                        productionCalendar,
                        plannedAllocation,
                        classification
                );

        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        ActualWorkInterval interval =
                mock(ActualWorkInterval.class);

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(interval)
        );

        when(
                actualAllocation.netSegments(
                        interval
                )
        ).thenReturn(
                List.of(
                        actualSegment(
                                "2026-08-18T21:00",
                                "2026-08-18T22:00"
                        )
                )
        );

        ZonedDateTime start =
                LocalDateTime.parse(
                        "2026-08-18T21:00"
                ).atZone(
                        ZoneId.of("UTC")
                );

        ZonedDateTime end =
                LocalDateTime.parse(
                        "2026-08-18T22:00"
                ).atZone(
                        ZoneId.of("UTC")
                );

        ClassificationSlice slice =
                new ClassificationSlice(
                        start.toLocalDateTime(),
                        end.toLocalDateTime(),
                        start.toInstant(),
                        end.toInstant(),
                        "UTC",
                        77L,
                        0,
                        60,
                        true,
                        false,
                        false,
                        false
                );

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                new DayClassification(
                        date,
                        60,
                        false,
                        PayClassificationService.DEFAULT_NIGHT_WINDOW,
                        60,
                        60,
                        0,
                        0,
                        0,
                        List.of(slice)
                )
        );

        OrdinaryPremiumSource result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(1, result.pieces().size());

        SourcePiece piece =
                result.pieces().get(0);

        assertEquals(SourceKind.EXPLICIT, piece.sourceKind());
        assertEquals(77L, piece.sourceActualWorkIntervalId());
        assertNull(piece.sourceDayEntryId());
        assertEquals(start.toInstant(), piece.sourceEvidenceStartInstant());
        assertEquals(end.toInstant(), piece.sourceEvidenceEndInstant());
        assertEquals("UTC", piece.sourceEvidenceTimezone());
        assertTrue(piece.deepIdentityComplete());
    }

    @Test
    void planDerivedSourceRetainsDayEntryAndCandidateClockEvidence() {
        ActualWorkIntervalRepository actualWork =
                mock(ActualWorkIntervalRepository.class);

        ActualWorkDayAllocationService actualAllocation =
                mock(ActualWorkDayAllocationService.class);

        DayEntryRepository days =
                mock(DayEntryRepository.class);

        VacationPlannerService vacationPlanner =
                mock(VacationPlannerService.class);

        ProductionCalendarService productionCalendar =
                mock(ProductionCalendarService.class);

        PlannedWorkDayAllocationService plannedAllocation =
                mock(PlannedWorkDayAllocationService.class);

        PayClassificationService classification =
                mock(PayClassificationService.class);

        OrdinaryWorkPremiumSourceService service =
                new OrdinaryWorkPremiumSourceService(
                        actualWork,
                        actualAllocation,
                        days,
                        vacationPlanner,
                        productionCalendar,
                        plannedAllocation,
                        classification
                );

        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        DayEntry day =
                mock(DayEntry.class);

        when(day.getId())
                .thenReturn(88L);

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        when(
                days.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.of(day)
        );

        when(
                productionCalendar.requiredMinutes(
                        user,
                        date,
                        day
                )
        ).thenReturn(120);

        when(
                vacationPlanner.occurrences(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        PlannedWorkDayAllocationService.NetWorkSegment planned =
                plannedSegment(
                        "2026-08-18T21:00",
                        "2026-08-18T23:00"
                );

        when(
                plannedAllocation.netSegments(
                        user,
                        day
                )
        ).thenReturn(
                List.of(planned)
        );

        when(
                productionCalendar.resolvedDay(
                        user,
                        date
                )
        ).thenReturn(null);

        OrdinaryPremiumSource result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(2, result.pieces().size());

        for (SourcePiece piece :
                result.pieces()) {

            assertEquals(
                    SourceKind.PLAN_DERIVED,
                    piece.sourceKind()
            );

            assertNull(
                    piece.sourceActualWorkIntervalId()
            );

            assertEquals(
                    88L,
                    piece.sourceDayEntryId()
            );

            /*
             * Both economic sub-pieces reference the complete candidate
             * planned net interval. No fake surviving subrange is invented.
             */
            assertEquals(
                    planned.startInstant(),
                    piece.sourceEvidenceStartInstant()
            );

            assertEquals(
                    planned.endInstant(),
                    piece.sourceEvidenceEndInstant()
            );

            assertEquals(
                    "UTC",
                    piece.sourceEvidenceTimezone()
            );

            assertTrue(
                    piece.deepIdentityComplete()
            );
        }
    }

    @Test
    void pricingProjectionRetainsSourcePiecesAndResolvedPricingSlices() {
        OrdinaryWorkPremiumSourceService sourceService =
                mock(
                        OrdinaryWorkPremiumSourceService.class
                );

        PayPricingPolicyService pricingPolicy =
                mock(
                        PayPricingPolicyService.class
                );

        HistoricalCompensationRateService historicalRates =
                mock(
                        HistoricalCompensationRateService.class
                );

        OrdinaryWorkPremiumPricingService service =
                new OrdinaryWorkPremiumPricingService(
                        sourceService,
                        pricingPolicy,
                        historicalRates,
                        new PayPricingEngine()
                );

        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        Instant start =
                Instant.parse(
                        "2026-08-18T22:00:00Z"
                );

        Instant end =
                Instant.parse(
                        "2026-08-18T23:00:00Z"
                );

        SourcePiece piece =
                new SourcePiece(
                        sourceDate,
                        SourceKind.EXPLICIT,
                        77L,
                        null,
                        start,
                        end,
                        "UTC",
                        60,
                        true,
                        false
                );

        when(
                sourceService.project(
                        eq(user),
                        any(LocalDate.class)
                )
        ).thenAnswer(invocation -> {
            LocalDate date =
                    invocation.getArgument(1);

            if (sourceDate.equals(date)) {
                return new OrdinaryPremiumSource(
                        date,
                        SourceKind.EXPLICIT,
                        60,
                        true,
                        null,
                        List.of(piece)
                );
            }

            return new OrdinaryPremiumSource(
                    date,
                    SourceKind.PLAN_DERIVED,
                    0,
                    true,
                    null,
                    List.of()
            );
        });

        PricingSlice resolvedSlice =
                new PricingSlice(
                        60,
                        List.of(
                                new PremiumComponent(
                                        "NIGHT",
                                        2_000
                                )
                        )
                );

        when(
                pricingPolicy.resolveForSourceDate(
                        eq(user),
                        eq(sourceDate),
                        anyList()
                )
        ).thenReturn(
                new ResolvedPricingPolicy(
                        sourceDate,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        new RuleSet(
                                List.of()
                        ),
                        List.of(
                                resolvedSlice
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        sourceDate
                )
        ).thenReturn(
                new HistoricalBaseRate(
                        sourceDate,
                        month,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        60_000L,
                        null
                )
        );

        var result =
                service.priceMonth(
                        user,
                        month
                );

        assertTrue(result.ready());
        assertEquals(1, result.sources().size());

        var valuation =
                result.sources().get(0);

        assertEquals(
                List.of(piece),
                valuation.sourcePieces()
        );

        assertEquals(
                List.of(resolvedSlice),
                valuation.pricingSlices()
        );

        assertTrue(
                valuation.deepIdentityComplete()
        );

        assertEquals(
                12_000L,
                result.premiumAmountMinor()
        );
    }

    private NetWorkSegment actualSegment(
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        return new NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                "UTC"
        );
    }

    private PlannedWorkDayAllocationService.NetWorkSegment plannedSegment(
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        return new PlannedWorkDayAllocationService.NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                "UTC"
        );
    }
}
