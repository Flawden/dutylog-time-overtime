package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.model.OvertimeSettlement;
import ru.daniil.shifts.model.OvertimeUsage;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.service.OvertimeAllocationProvenanceService.AllocationProvenance;
import ru.daniil.shifts.service.OvertimeAllocationProvenanceService.ConsumedProvenancePiece;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayPricingPolicyService.ResolvedPricingPolicy;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OvertimeSettlementPricingSourceServiceTest {

    private final OvertimeSettlementRepository settlements =
            mock(OvertimeSettlementRepository.class);

    private final OvertimeUsageRepository usages =
            mock(OvertimeUsageRepository.class);

    private final OvertimeAllocationRepository allocations =
            mock(OvertimeAllocationRepository.class);

    private final OvertimeAllocationProvenanceService provenance =
            mock(OvertimeAllocationProvenanceService.class);

    private final PayPricingPolicyService pricingPolicy =
            mock(PayPricingPolicyService.class);

    private final OvertimeSettlementPricingSourceService service =
            new OvertimeSettlementPricingSourceService(
                    settlements,
                    usages,
                    allocations,
                    provenance,
                    pricingPolicy
            );

    private final AppUser user =
            new AppUser(
                    "settlement-pricing-user",
                    "{noop}unused"
            );

    @Test
    void pricingTierUsesCreditOffsetNotFactualWorkedOrdinal() {
        long settlementId = 7L;

        OvertimeSettlement settlement =
                settlement(
                        settlementId,
                        "2026-08-20",
                        60
                );

        OvertimeUsage usage =
                usage(
                        11L,
                        settlementId,
                        60
                );

        OvertimeAllocation allocation =
                allocation(
                        13L,
                        60
                );

        when(
                settlements.findByOwnerAndId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(settlement)
        );

        when(
                usages.findByOwnerAndSourceSettlementId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(usage)
        );

        when(
                allocations.findByUsage(
                        usage
                )
        ).thenReturn(
                List.of(allocation)
        );

        LocalDate sourceDate =
                LocalDate.parse(
                        "2026-08-10"
                );

        ConsumedProvenancePiece piece =
                piece(
                        30,
                        60,
                        sourceDate,
                        true,
                        false,
                        510
                );

        when(
                provenance.resolve(
                        allocation
                )
        ).thenReturn(
                new AllocationProvenance(
                        13L,
                        17L,
                        30,
                        60,
                        true,
                        60,
                        List.of(piece)
                )
        );

        ConsumedSlice expectedConsumed =
                new ConsumedSlice(
                        60,
                        true,
                        false,
                        30
                );

        when(
                pricingPolicy.resolveForSourceDate(
                        user,
                        sourceDate,
                        List.of(expectedConsumed)
                )
        ).thenReturn(
                baseOnlyPolicy(
                        sourceDate,
                        LocalDate.parse(
                                "2026-08-01"
                        ),
                        60
                )
        );

        var result =
                service.project(
                        user,
                        settlementId
                );

        assertEquals(
                1,
                result.pieces().size()
        );

        var projected =
                result.pieces().get(0);

        assertEquals(
                30,
                projected.overtimeCreditOffsetStartMinutes()
        );

        assertEquals(
                510,
                projected.factualWorkedOrdinalStartMinutes()
        );

        verify(
                pricingPolicy
        ).resolveForSourceDate(
                user,
                sourceDate,
                List.of(expectedConsumed)
        );
    }

    @Test
    void unknownLegacyOrManualProvenanceFailsClosedBeforePricing() {
        long settlementId = 21L;

        OvertimeSettlement settlement =
                settlement(
                        settlementId,
                        "2026-08-20",
                        60
                );

        OvertimeUsage usage =
                usage(
                        22L,
                        settlementId,
                        60
                );

        OvertimeAllocation allocation =
                allocation(
                        23L,
                        60
                );

        when(
                settlements.findByOwnerAndId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(settlement)
        );

        when(
                usages.findByOwnerAndSourceSettlementId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(usage)
        );

        when(
                allocations.findByUsage(
                        usage
                )
        ).thenReturn(
                List.of(allocation)
        );

        when(
                provenance.resolve(
                        allocation
                )
        ).thenReturn(
                new AllocationProvenance(
                        23L,
                        24L,
                        0,
                        60,
                        false,
                        0,
                        List.of()
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.project(
                                        user,
                                        settlementId
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "происхождение минут"
                        )
        );

        verifyNoInteractions(
                pricingPolicy
        );
    }

    @Test
    void differentHistoricalSourceDatesResolvePricingIndependently() {
        long settlementId = 31L;

        OvertimeSettlement settlement =
                settlement(
                        settlementId,
                        "2026-09-05",
                        120
                );

        OvertimeUsage usage =
                usage(
                        32L,
                        settlementId,
                        120
                );

        OvertimeAllocation first =
                allocation(
                        33L,
                        60
                );

        OvertimeAllocation second =
                allocation(
                        34L,
                        60
                );

        when(
                settlements.findByOwnerAndId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(settlement)
        );

        when(
                usages.findByOwnerAndSourceSettlementId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(usage)
        );

        when(
                allocations.findByUsage(
                        usage
                )
        ).thenReturn(
                List.of(
                        second,
                        first
                )
        );

        LocalDate july =
                LocalDate.parse(
                        "2026-07-31"
                );

        LocalDate august =
                LocalDate.parse(
                        "2026-08-15"
                );

        when(
                provenance.resolve(
                        first
                )
        ).thenReturn(
                new AllocationProvenance(
                        33L,
                        40L,
                        0,
                        60,
                        true,
                        60,
                        List.of(
                                piece(
                                        0,
                                        60,
                                        july,
                                        false,
                                        false,
                                        480
                                )
                        )
                )
        );

        when(
                provenance.resolve(
                        second
                )
        ).thenReturn(
                new AllocationProvenance(
                        34L,
                        41L,
                        0,
                        60,
                        true,
                        60,
                        List.of(
                                piece(
                                        0,
                                        60,
                                        august,
                                        true,
                                        false,
                                        480
                                )
                        )
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        user,
                        july,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        false,
                                        false,
                                        0
                                )
                        )
                )
        ).thenReturn(
                baseOnlyPolicy(
                        july,
                        LocalDate.parse(
                                "2026-06-01"
                        ),
                        60
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        user,
                        august,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        true,
                                        false,
                                        0
                                )
                        )
                )
        ).thenReturn(
                baseOnlyPolicy(
                        august,
                        LocalDate.parse(
                                "2026-08-10"
                        ),
                        60
                )
        );

        var result =
                service.project(
                        user,
                        settlementId
                );

        assertEquals(
                120,
                result.requestedMinutes()
        );

        assertEquals(
                120,
                result.allocatedMinutes()
        );

        assertEquals(
                2,
                result.pieces().size()
        );

        assertEquals(
                july,
                result.pieces().get(0)
                        .sourceDate()
        );

        assertEquals(
                august,
                result.pieces().get(1)
                        .sourceDate()
        );

        assertEquals(
                LocalDate.parse(
                        "2026-06-01"
                ),
                result.pieces().get(0)
                        .pricingEffectiveFrom()
        );

        assertEquals(
                LocalDate.parse(
                        "2026-08-10"
                ),
                result.pieces().get(1)
                        .pricingEffectiveFrom()
        );
    }

    @Test
    void fifoMinuteMismatchFailsBeforeProvenanceIsPriced() {
        long settlementId = 51L;

        OvertimeSettlement settlement =
                settlement(
                        settlementId,
                        "2026-08-20",
                        60
                );

        OvertimeUsage usage =
                usage(
                        52L,
                        settlementId,
                        60
                );

        OvertimeAllocation allocation =
                allocation(
                        53L,
                        30
                );

        when(
                settlements.findByOwnerAndId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(settlement)
        );

        when(
                usages.findByOwnerAndSourceSettlementId(
                        user,
                        settlementId
                )
        ).thenReturn(
                Optional.of(usage)
        );

        when(
                allocations.findByUsage(
                        usage
                )
        ).thenReturn(
                List.of(allocation)
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.project(
                                        user,
                                        settlementId
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "FIFO allocation"
                        )
        );

        verifyNoInteractions(
                provenance,
                pricingPolicy
        );
    }

    private OvertimeSettlement settlement(
            long id,
            String date,
            int minutes
    ) {
        OvertimeSettlement settlement =
                mock(
                        OvertimeSettlement.class
                );

        when(
                settlement.getId()
        ).thenReturn(
                id
        );

        when(
                settlement.getSettlementDate()
        ).thenReturn(
                LocalDate.parse(date)
        );

        when(
                settlement.getRequestedMinutes()
        ).thenReturn(
                minutes
        );

        return settlement;
    }

    private OvertimeUsage usage(
            long id,
            long settlementId,
            int minutes
    ) {
        OvertimeUsage usage =
                mock(
                        OvertimeUsage.class
                );

        when(
                usage.getId()
        ).thenReturn(
                id
        );

        when(
                usage.getSourceSettlementId()
        ).thenReturn(
                settlementId
        );

        when(
                usage.getRequestedMinutes()
        ).thenReturn(
                minutes
        );

        when(
                usage.isSettlementLinked()
        ).thenReturn(
                true
        );

        return usage;
    }

    private OvertimeAllocation allocation(
            long id,
            int minutes
    ) {
        OvertimeAllocation allocation =
                mock(
                        OvertimeAllocation.class
                );

        when(
                allocation.getId()
        ).thenReturn(
                id
        );

        when(
                allocation.getAllocatedMinutes()
        ).thenReturn(
                minutes
        );

        when(
                allocation.getCreditOffsetStartMinutes()
        ).thenReturn(
                0
        );

        return allocation;
    }

    private ConsumedProvenancePiece piece(
            int creditOffset,
            int minutes,
            LocalDate sourceDate,
            boolean night,
            boolean holiday,
            int factualOrdinal
    ) {
        LocalDateTime start =
                sourceDate.atTime(
                        22,
                        0
                );

        return new ConsumedProvenancePiece(
                creditOffset,
                minutes,
                100L + creditOffset,
                sourceDate,
                start,
                start.plusMinutes(minutes),
                null,
                null,
                null,
                false,
                night,
                holiday,
                factualOrdinal
        );
    }

    private ResolvedPricingPolicy baseOnlyPolicy(
            LocalDate sourceDate,
            LocalDate effectiveFrom,
            int minutes
    ) {
        return new ResolvedPricingPolicy(
                sourceDate,
                effectiveFrom,
                new RuleSet(
                        List.of()
                ),
                List.of(
                        new PricingSlice(
                                minutes,
                                List.of()
                        )
                )
        );
    }
}
