package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeCreditSlice;
import ru.daniil.shifts.repo.OvertimeCreditSliceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves which factual/classified source minutes were consumed by one
 * canonical FIFO allocation.
 *
 * This is a pure provenance projection:
 *
 * allocation credit offset range
 *          ×
 * OvertimeCreditSlice ranges
 *          =
 * exact consumed source pieces.
 *
 * No pricing, settlement policy, multiplier or money belongs here.
 */
@Service
public class OvertimeAllocationProvenanceService {

    private final OvertimeCreditSliceRepository slices;

    public OvertimeAllocationProvenanceService(
            OvertimeCreditSliceRepository slices
    ) {
        this.slices = slices;
    }

    @Transactional(readOnly = true)
    public AllocationProvenance resolve(
            OvertimeAllocation allocation
    ) {
        if (allocation == null) {
            throw new IllegalArgumentException(
                    "Allocation provenance requires allocation"
            );
        }

        OvertimeCredit credit =
                allocation.getCredit();

        if (credit == null) {
            throw new IllegalArgumentException(
                    "Allocation provenance requires credit"
            );
        }

        int allocatedMinutes =
                allocation.getAllocatedMinutes();

        if (allocatedMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Allocation provenance requires positive minutes"
            );
        }

        int allocationStart =
                allocation.getCreditOffsetStartMinutes();

        int allocationEnd =
                Math.addExact(
                        allocationStart,
                        allocatedMinutes
                );

        if (allocationEnd
                > credit.getCreditedMinutes()) {
            throw new IllegalStateException(
                    "Allocation exceeds overtime credit minute range"
            );
        }

        List<OvertimeCreditSlice> creditSlices =
                slices.findByCreditOrderByOffsetStartMinutesAscIdAsc(
                        credit
                );

        /*
         * MANUAL credits and pre-V55 aggregate SYSTEM_ACTUAL_WORK credits may
         * legitimately have no factual slice provenance.
         *
         * Unknown is preserved as unknown instead of inventing a source.
         */
        if (creditSlices.isEmpty()) {
            return new AllocationProvenance(
                    allocation.getId(),
                    credit.getId(),
                    allocationStart,
                    allocatedMinutes,
                    false,
                    0,
                    List.of()
            );
        }

        List<ConsumedProvenancePiece> pieces =
                new ArrayList<>();

        int coveredMinutes = 0;

        for (OvertimeCreditSlice slice :
                creditSlices) {

            int sliceStart =
                    slice.getOffsetStartMinutes();

            int sliceEnd =
                    Math.addExact(
                            sliceStart,
                            slice.getMinutes()
                    );

            int overlapStart =
                    Math.max(
                            allocationStart,
                            sliceStart
                    );

            int overlapEnd =
                    Math.min(
                            allocationEnd,
                            sliceEnd
                    );

            if (overlapEnd <= overlapStart) {
                continue;
            }

            int relativeStart =
                    overlapStart - sliceStart;

            int overlapMinutes =
                    overlapEnd - overlapStart;

            pieces.add(
                    piece(
                            slice,
                            overlapStart,
                            relativeStart,
                            overlapMinutes
                    )
            );

            coveredMinutes += overlapMinutes;
        }

        /*
         * Once a credit has slice provenance, partial provenance is corruption.
         * New SYSTEM_ACTUAL_WORK credits are persisted with complete contiguous
         * coverage. We therefore fail closed instead of silently returning an
         * incomplete settlement source.
         */
        if (coveredMinutes != allocatedMinutes) {
            throw new IllegalStateException(
                    "Stored overtime provenance covers "
                            + coveredMinutes
                            + " of "
                            + allocatedMinutes
                            + " allocated minutes"
            );
        }

        int expectedCreditOffset =
                allocationStart;

        for (ConsumedProvenancePiece piece :
                pieces) {

            if (piece.creditOffsetStartMinutes()
                    != expectedCreditOffset) {
                throw new IllegalStateException(
                        "Consumed provenance pieces are not contiguous"
                );
            }

            expectedCreditOffset +=
                    piece.minutes();
        }

        if (expectedCreditOffset
                != allocationEnd) {
            throw new IllegalStateException(
                    "Consumed provenance does not reach allocation end"
            );
        }

        return new AllocationProvenance(
                allocation.getId(),
                credit.getId(),
                allocationStart,
                allocatedMinutes,
                true,
                coveredMinutes,
                List.copyOf(pieces)
        );
    }

    private ConsumedProvenancePiece piece(
            OvertimeCreditSlice slice,
            int creditOffsetStart,
            int relativeStart,
            int minutes
    ) {
        if (!slice.exact()) {
            LocalDateTime start =
                    slice.getSourceStartAt()
                            .plusMinutes(
                                    relativeStart
                            );

            LocalDateTime end =
                    start.plusMinutes(minutes);

            return new ConsumedProvenancePiece(
                    creditOffsetStart,
                    minutes,
                    slice.getSourceActualWorkInterval()
                            .getId(),
                    slice.getSourceDate(),
                    start,
                    end,
                    null,
                    null,
                    null,
                    false,
                    slice.isNight(),
                    slice.isHoliday(),
                    slice.getOvertimeOrdinalStartMinutes()
                            + relativeStart
            );
        }

        ZoneId zone =
                ZoneId.of(
                        slice.getSourceTimezone()
                );

        Instant startInstant =
                slice.getSourceStartInstant()
                        .plusSeconds(
                                relativeStart * 60L
                        );

        Instant endInstant =
                startInstant.plusSeconds(
                        minutes * 60L
                );

        return new ConsumedProvenancePiece(
                creditOffsetStart,
                minutes,
                slice.getSourceActualWorkInterval()
                        .getId(),
                slice.getSourceDate(),
                startInstant.atZone(zone)
                        .toLocalDateTime(),
                endInstant.atZone(zone)
                        .toLocalDateTime(),
                startInstant,
                endInstant,
                zone.getId(),
                true,
                slice.isNight(),
                slice.isHoliday(),
                slice.getOvertimeOrdinalStartMinutes()
                        + relativeStart
        );
    }

    public record AllocationProvenance(
            Long allocationId,
            Long creditId,
            int creditOffsetStartMinutes,
            int allocatedMinutes,
            boolean provenanceKnown,
            int coveredMinutes,
            List<ConsumedProvenancePiece> pieces
    ) {
        public AllocationProvenance {
            if (creditOffsetStartMinutes < 0
                    || allocatedMinutes <= 0
                    || coveredMinutes < 0
                    || coveredMinutes > allocatedMinutes) {
                throw new IllegalArgumentException(
                        "Invalid allocation provenance minute totals"
                );
            }

            pieces = pieces == null
                    ? List.of()
                    : List.copyOf(pieces);

            if (!provenanceKnown
                    && (!pieces.isEmpty()
                    || coveredMinutes != 0)) {
                throw new IllegalArgumentException(
                        "Unknown provenance cannot expose source pieces"
                );
            }

            if (provenanceKnown
                    && coveredMinutes != allocatedMinutes) {
                throw new IllegalArgumentException(
                        "Known provenance must cover complete allocation"
                );
            }
        }
    }

    public record ConsumedProvenancePiece(
            int creditOffsetStartMinutes,
            int minutes,
            Long sourceActualWorkIntervalId,
            LocalDate sourceDate,
            LocalDateTime sourceStartAt,
            LocalDateTime sourceEndAt,
            Instant sourceStartInstant,
            Instant sourceEndInstant,
            String sourceTimezone,
            boolean exact,
            boolean night,
            boolean holiday,
            int overtimeOrdinalStartMinutes
    ) {
        public ConsumedProvenancePiece {
            if (creditOffsetStartMinutes < 0
                    || minutes <= 0
                    || sourceActualWorkIntervalId == null
                    || sourceActualWorkIntervalId <= 0
                    || sourceDate == null
                    || sourceStartAt == null
                    || sourceEndAt == null
                    || overtimeOrdinalStartMinutes < 0) {
                throw new IllegalArgumentException(
                        "Invalid consumed overtime provenance"
                );
            }

            boolean anyAbsolute =
                    sourceStartInstant != null
                            || sourceEndInstant != null
                            || sourceTimezone != null;

            if (exact != anyAbsolute) {
                throw new IllegalArgumentException(
                        "Consumed provenance exact flag disagrees with identity"
                );
            }

            if (exact) {
                if (sourceStartInstant == null
                        || sourceEndInstant == null
                        || sourceTimezone == null
                        || sourceTimezone.isBlank()
                        || !sourceEndInstant.isAfter(
                                sourceStartInstant
                        )) {
                    throw new IllegalArgumentException(
                            "Exact consumed provenance requires complete identity"
                    );
                }
            } else if (!sourceEndAt.isAfter(
                    sourceStartAt
            )) {
                throw new IllegalArgumentException(
                        "Legacy consumed provenance requires positive local duration"
                );
            }
        }
    }
}
