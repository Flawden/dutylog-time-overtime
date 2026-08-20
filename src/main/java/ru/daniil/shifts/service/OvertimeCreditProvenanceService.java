package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeCreditSlice;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.OvertimeCreditSliceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns factual/classification provenance below SYSTEM_ACTUAL_WORK credits.
 *
 * Credit balance remains owned by OvertimeService. This service only replaces
 * the homogeneous source slices after the bank reconciliation has succeeded.
 *
 * No pricing, multiplier, settlement or payroll money belongs here.
 */
@Service
public class OvertimeCreditProvenanceService {

    private static final String SYSTEM_ACTUAL_WORK =
            "SYSTEM_ACTUAL_WORK";

    private final OvertimeCreditRepository credits;
    private final OvertimeCreditSliceRepository slices;
    private final ActualWorkIntervalRepository actualWork;

    public OvertimeCreditProvenanceService(
            OvertimeCreditRepository credits,
            OvertimeCreditSliceRepository slices,
            ActualWorkIntervalRepository actualWork
    ) {
        this.credits = credits;
        this.slices = slices;
        this.actualWork = actualWork;
    }

    /**
     * Atomically replaces provenance for one derived daily credit.
     *
     * The caller must reconcile the SYSTEM_ACTUAL_WORK credit first inside the
     * same outer transaction. Therefore a failure here rolls the complete
     * credit + provenance change back together.
     */
    @Transactional
    public void replaceSystemActualWorkProvenance(
            AppUser user,
            LocalDate date,
            List<OvertimeProvenanceDraft> drafts
    ) {
        if (user == null || date == null) {
            throw new IllegalArgumentException(
                    "Provenance replacement requires user and date"
            );
        }

        List<OvertimeProvenanceDraft> safeDrafts =
                drafts == null
                        ? List.of()
                        : List.copyOf(drafts);

        OvertimeCredit credit =
                credits
                        .findByOwnerAndWorkDateAndSourceKind(
                                user,
                                date,
                                SYSTEM_ACTUAL_WORK
                        )
                        .orElse(null);

        /*
         * Existing MANUAL provenance intentionally wins over automatic derived
         * credit creation. In that compatibility case there is no system credit
         * to attach factual slices to.
         */
        if (credit == null) {
            if (safeDrafts.isEmpty()) {
                return;
            }

            boolean manualExists =
                    credits
                            .findByOwnerAndWorkDateOrderByIdAsc(
                                    user,
                                    date
                            )
                            .stream()
                            .anyMatch(item ->
                                    !item.isSystemActualWorkDerived()
                            );

            if (manualExists) {
                return;
            }

            throw new IllegalStateException(
                    "Classification produced overtime provenance "
                            + "without SYSTEM_ACTUAL_WORK credit"
            );
        }

        if (!credit.isSystemActualWorkDerived()) {
            throw new IllegalStateException(
                    "Provenance target is not SYSTEM_ACTUAL_WORK"
            );
        }

        int draftMinutes =
                safeDrafts.stream()
                        .mapToInt(
                                OvertimeProvenanceDraft::minutes
                        )
                        .sum();

        if (draftMinutes
                != credit.getCreditedMinutes()) {
            throw new IllegalStateException(
                    "Provenance minute total "
                            + draftMinutes
                            + " does not match credit "
                            + credit.getCreditedMinutes()
            );
        }

        /*
         * Resolve every source before deleting existing provenance.
         * Transaction rollback protects us anyway, but validating the complete
         * replacement first keeps the write phase deliberately boring.
         */
        Map<Long, ActualWorkInterval> sources =
                new LinkedHashMap<>();

        for (OvertimeProvenanceDraft draft :
                safeDrafts) {

            ActualWorkInterval source =
                    sources.computeIfAbsent(
                            draft.sourceActualWorkIntervalId(),
                            sourceId ->
                                    actualWork
                                            .findByOwnerAndId(
                                                    user,
                                                    sourceId
                                            )
                                            .orElseThrow(() ->
                                                    new IllegalStateException(
                                                            "Actual Work provenance source "
                                                                    + sourceId
                                                                    + " is missing"
                                                    )
                                            )
                    );

            if (!date.equals(
                    source.getWorkDate()
            ) && !date.equals(
                    source.getEndDate()
            )) {
                throw new IllegalStateException(
                        "Actual Work provenance source does not cover "
                                + date
                );
            }
        }

        slices.deleteByCredit(credit);
        slices.flush();

        int creditOffset = 0;

        for (OvertimeProvenanceDraft draft :
                safeDrafts) {

            ActualWorkInterval source =
                    sources.get(
                            draft.sourceActualWorkIntervalId()
                    );

            OvertimeCreditSlice slice =
                    new OvertimeCreditSlice(
                            credit,
                            creditOffset,
                            draft.minutes(),
                            source,
                            date,
                            draft.sourceStartAt(),
                            draft.sourceEndAt(),
                            draft.sourceStartInstant(),
                            draft.sourceEndInstant(),
                            draft.sourceTimezone(),
                            draft.night(),
                            draft.holiday(),
                            draft.overtimeOrdinalStartMinutes()
                    );

            slices.save(slice);
            creditOffset += draft.minutes();
        }

        slices.flush();

        List<OvertimeCreditSlice> persisted =
                slices
                        .findByCreditOrderByOffsetStartMinutesAscIdAsc(
                                credit
                        );

        int persistedMinutes =
                persisted.stream()
                        .mapToInt(
                                OvertimeCreditSlice::getMinutes
                        )
                        .sum();

        if (persistedMinutes
                != credit.getCreditedMinutes()) {
            throw new IllegalStateException(
                    "Persisted provenance total changed unexpectedly"
            );
        }

        int expectedOffset = 0;

        for (OvertimeCreditSlice slice :
                persisted) {

            if (slice.getOffsetStartMinutes()
                    != expectedOffset) {
                throw new IllegalStateException(
                        "Persisted provenance offsets are not contiguous"
                );
            }

            expectedOffset += slice.getMinutes();
        }

        if (expectedOffset
                != credit.getCreditedMinutes()) {
            throw new IllegalStateException(
                    "Persisted provenance does not cover complete credit"
            );
        }
    }

    public record OvertimeProvenanceDraft(
            Long sourceActualWorkIntervalId,
            LocalDateTime sourceStartAt,
            LocalDateTime sourceEndAt,
            Instant sourceStartInstant,
            Instant sourceEndInstant,
            String sourceTimezone,
            int minutes,
            boolean night,
            boolean holiday,
            int overtimeOrdinalStartMinutes
    ) {
        public OvertimeProvenanceDraft {
            if (sourceActualWorkIntervalId == null
                    || sourceActualWorkIntervalId <= 0) {
                throw new IllegalArgumentException(
                        "Provenance requires persisted Actual Work id"
                );
            }

            if (sourceStartAt == null
                    || sourceEndAt == null) {
                throw new IllegalArgumentException(
                        "Provenance requires source-local boundaries"
                );
            }

            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Provenance must contain positive minutes"
                );
            }

            if (overtimeOrdinalStartMinutes < 0) {
                throw new IllegalArgumentException(
                        "Provenance overtime ordinal cannot be negative"
                );
            }

            boolean anyAbsolute =
                    sourceStartInstant != null
                            || sourceEndInstant != null
                            || sourceTimezone != null;

            if (anyAbsolute) {
                if (sourceStartInstant == null
                        || sourceEndInstant == null
                        || sourceTimezone == null
                        || sourceTimezone.isBlank()
                        || !sourceEndInstant.isAfter(
                                sourceStartInstant
                        )) {
                    throw new IllegalArgumentException(
                            "Exact provenance draft requires complete absolute identity"
                    );
                }
            } else if (!sourceEndAt.isAfter(
                    sourceStartAt
            )) {
                throw new IllegalArgumentException(
                        "Legacy provenance draft requires positive local duration"
                );
            }
        }
    }
}
