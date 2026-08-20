package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only bridge from one explicit cash settlement to factual pricing input.
 *
 * Settlement owns the decision.
 * OvertimeUsage / OvertimeAllocation own the canonical FIFO debit.
 * OvertimeAllocationProvenanceService owns consumed factual provenance.
 * PayPricingPolicyService owns effective source-date pricing policy.
 *
 * This service deliberately calculates NO MONEY.
 */
@Service
public class OvertimeSettlementPricingSourceService {

    private final OvertimeSettlementRepository settlements;
    private final OvertimeUsageRepository usages;
    private final OvertimeAllocationRepository allocations;
    private final OvertimeAllocationProvenanceService provenance;
    private final PayPricingPolicyService pricingPolicy;

    public OvertimeSettlementPricingSourceService(
            OvertimeSettlementRepository settlements,
            OvertimeUsageRepository usages,
            OvertimeAllocationRepository allocations,
            OvertimeAllocationProvenanceService provenance,
            PayPricingPolicyService pricingPolicy
    ) {
        this.settlements = settlements;
        this.usages = usages;
        this.allocations = allocations;
        this.provenance = provenance;
        this.pricingPolicy = pricingPolicy;
    }

    @Transactional(readOnly = true)
    public SettlementPricingSource project(
            AppUser user,
            Long settlementId
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Settlement pricing source requires user"
            );
        }

        if (settlementId == null) {
            throw ApiException.notFound(
                    "Settlement переработки не найден"
            );
        }

        OvertimeSettlement settlement =
                settlements
                        .findByOwnerAndId(
                                user,
                                settlementId
                        )
                        .orElseThrow(() ->
                                ApiException.notFound(
                                        "Settlement переработки не найден"
                                )
                        );

        OvertimeUsage usage =
                usages
                        .findByOwnerAndSourceSettlementId(
                                user,
                                settlementId
                        )
                        .orElseThrow(() ->
                                ApiException.conflict(
                                        "SETTLEMENT_FIFO_USAGE_REQUIRED",
                                        "Settlement не имеет связанного списания Time Bank"
                                )
                        );

        if (!usage.isSettlementLinked()
                || !settlementId.equals(
                        usage.getSourceSettlementId()
                )) {
            throw new IllegalStateException(
                    "Settlement-linked overtime usage identity is inconsistent"
            );
        }

        int requestedMinutes =
                settlement.getRequestedMinutes();

        if (usage.getRequestedMinutes()
                != requestedMinutes) {
            throw ApiException.conflict(
                    "SETTLEMENT_FIFO_MISMATCH",
                    "Минуты settlement не совпадают со связанным списанием Time Bank"
            );
        }

        List<OvertimeAllocation> settlementAllocations =
                allocations
                        .findByUsage(usage)
                        .stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                OvertimeAllocation::getId,
                                                Comparator.nullsLast(
                                                        Long::compareTo
                                                )
                                        )
                                        .thenComparingInt(
                                                OvertimeAllocation::getCreditOffsetStartMinutes
                                        )
                        )
                        .toList();

        if (settlementAllocations.isEmpty()) {
            throw ApiException.conflict(
                    "SETTLEMENT_FIFO_ALLOCATION_REQUIRED",
                    "Settlement не имеет FIFO allocation"
            );
        }

        int allocatedMinutes =
                settlementAllocations
                        .stream()
                        .mapToInt(
                                OvertimeAllocation::getAllocatedMinutes
                        )
                        .sum();

        if (allocatedMinutes != requestedMinutes) {
            throw ApiException.conflict(
                    "SETTLEMENT_FIFO_MISMATCH",
                    "FIFO allocation settlement не покрывает все запрошенные минуты"
            );
        }

        List<SourcePiece> pieces =
                settlementAllocations
                        .stream()
                        .flatMap(allocation ->
                                projectAllocation(
                                        user,
                                        allocation
                                ).stream()
                        )
                        .toList();

        int provenanceMinutes =
                pieces.stream()
                        .mapToInt(
                                SourcePiece::minutes
                        )
                        .sum();

        if (provenanceMinutes
                != requestedMinutes) {
            throw new IllegalStateException(
                    "Settlement pricing provenance minute total changed"
            );
        }

        return new SettlementPricingSource(
                settlement.getId(),
                settlement.getSettlementDate(),
                usage.getId(),
                requestedMinutes,
                allocatedMinutes,
                List.copyOf(pieces)
        );
    }

    private List<SourcePiece> projectAllocation(
            AppUser user,
            OvertimeAllocation allocation
    ) {
        AllocationProvenance resolved =
                provenance.resolve(
                        allocation
                );

        if (!resolved.provenanceKnown()) {
            throw ApiException.conflict(
                    "PAY_PRICING_PROVENANCE_REQUIRED",
                    "Для автоматически рассчитанной выплаты требуется происхождение минут переработки"
            );
        }

        if (resolved.allocatedMinutes()
                != allocation.getAllocatedMinutes()) {
            throw new IllegalStateException(
                    "Allocation provenance minute total disagrees with FIFO allocation"
            );
        }

        return resolved
                .pieces()
                .stream()
                .map(piece ->
                        projectPiece(
                                user,
                                resolved,
                                piece
                        )
                )
                .toList();
    }

    private SourcePiece projectPiece(
            AppUser user,
            AllocationProvenance allocation,
            ConsumedProvenancePiece piece
    ) {
        /*
         * CRITICAL:
         *
         * Pricing overtime tiers are relative to the overtime CREDIT:
         * credit offset 0 == first overtime minute.
         *
         * overtimeOrdinalStartMinutes is the ordinal inside all factual work
         * of the source day (for example 480 after an ordinary 8h threshold)
         * and MUST NOT be used as the pricing-tier offset.
         */
        ConsumedSlice consumed =
                new ConsumedSlice(
                        piece.minutes(),
                        piece.night(),
                        piece.holiday(),
                        piece.creditOffsetStartMinutes()
                );

        PayPricingPolicyService.ResolvedPricingPolicy policy =
                pricingPolicy.resolveForSourceDate(
                        user,
                        piece.sourceDate(),
                        List.of(consumed)
                );

        int pricedMinutes =
                policy.pricingSlices()
                        .stream()
                        .mapToInt(
                                PricingSlice::minutes
                        )
                        .sum();

        if (pricedMinutes
                != piece.minutes()) {
            throw new IllegalStateException(
                    "Resolved pricing slices changed provenance minutes"
            );
        }

        return new SourcePiece(
                allocation.allocationId(),
                allocation.creditId(),
                piece.sourceActualWorkIntervalId(),
                piece.sourceDate(),
                piece.minutes(),
                piece.night(),
                piece.holiday(),
                piece.creditOffsetStartMinutes(),
                piece.overtimeOrdinalStartMinutes(),
                policy.effectiveFrom(),
                policy.pricingSlices()
        );
    }

    public record SettlementPricingSource(
            Long settlementId,
            LocalDate settlementDate,
            Long usageId,
            int requestedMinutes,
            int allocatedMinutes,
            List<SourcePiece> pieces
    ) {
        public SettlementPricingSource {
            if (settlementId == null
                    || settlementDate == null
                    || usageId == null
                    || requestedMinutes <= 0
                    || allocatedMinutes != requestedMinutes) {
                throw new IllegalArgumentException(
                        "Invalid settlement pricing source identity"
                );
            }

            pieces =
                    pieces == null
                            ? List.of()
                            : List.copyOf(pieces);

            int sourceMinutes =
                    pieces.stream()
                            .mapToInt(
                                    SourcePiece::minutes
                            )
                            .sum();

            if (sourceMinutes
                    != requestedMinutes) {
                throw new IllegalArgumentException(
                        "Settlement pricing source must preserve all requested minutes"
                );
            }
        }
    }

    public record SourcePiece(
            Long allocationId,
            Long creditId,
            Long sourceActualWorkIntervalId,
            LocalDate sourceDate,
            int minutes,
            boolean night,
            boolean holiday,
            int overtimeCreditOffsetStartMinutes,
            int factualWorkedOrdinalStartMinutes,
            LocalDate pricingEffectiveFrom,
            List<PricingSlice> pricingSlices
    ) {
        public SourcePiece {
            if (sourceActualWorkIntervalId == null
                    || sourceDate == null
                    || minutes <= 0
                    || overtimeCreditOffsetStartMinutes < 0
                    || factualWorkedOrdinalStartMinutes < 0
                    || pricingEffectiveFrom == null
                    || pricingEffectiveFrom.isAfter(
                            sourceDate
                    )) {
                throw new IllegalArgumentException(
                        "Invalid settlement pricing source piece"
                );
            }

            pricingSlices =
                    pricingSlices == null
                            ? List.of()
                            : List.copyOf(
                                    pricingSlices
                            );

            int pricedMinutes =
                    pricingSlices.stream()
                            .mapToInt(
                                    PricingSlice::minutes
                            )
                            .sum();

            if (pricedMinutes != minutes) {
                throw new IllegalArgumentException(
                        "Pricing slices must preserve source-piece minutes"
                );
            }
        }
    }
}
