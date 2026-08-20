package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.OvertimeCreditProvenanceService.OvertimeProvenanceDraft;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationService.DayClassification;

import java.time.LocalDate;
import java.util.List;

/**
 * Reconciles consequences that are fully derivable from explicit factual work.
 *
 * Native Pay Classification decides which factual minutes are overtime.
 * OvertimeService projects their aggregate into the canonical FIFO Time Bank.
 * OvertimeCreditProvenanceService then persists the exact classified source
 * slices under that SYSTEM_ACTUAL_WORK credit.
 *
 * Production norm, pricing and payroll money are not rederived here.
 */
@Service
public class WorkdayDerivedCompensationService {

    private final PayClassificationService classification;
    private final OvertimeService overtime;
    private final OvertimeCreditProvenanceService provenance;

    public WorkdayDerivedCompensationService(
            PayClassificationService classification,
            OvertimeService overtime,
            OvertimeCreditProvenanceService provenance
    ) {
        this.classification = classification;
        this.overtime = overtime;
        this.provenance = provenance;
    }

    @Transactional
    public void reconcile(
            AppUser user,
            LocalDate date
    ) {
        reconcileRange(user, date, date);
    }

    @Transactional
    public void reconcileRange(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        for (
                LocalDate date = from;
                !date.isAfter(to);
                date = date.plusDays(1)
        ) {
            reconcileDate(
                    user,
                    date,
                    false
            );
        }
    }

    /**
     * Temporal Work Context correction path.
     *
     * Live factual identity/classification/provenance may be reconstructed
     * inside a closed month while immutable Payroll snapshots remain untouched.
     * Existing Time-Bank usage/shrink protection remains authoritative.
     */
    @Transactional
    public void reconcileRangeHistoricalCorrection(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        for (
                LocalDate date = from;
                !date.isAfter(to);
                date = date.plusDays(1)
        ) {
            reconcileDate(
                    user,
                    date,
                    true
            );
        }
    }

    private void reconcileDate(
            AppUser user,
            LocalDate date,
            boolean historicalCorrection
    ) {
        DayClassification day =
                classification.classify(
                        user,
                        date
                );

        int targetMinutes =
                day.overtimeMinutes();

        int ordinaryThresholdMinutes =
                day.ordinaryThresholdMinutes();

        List<OvertimeProvenanceDraft> provenanceDrafts =
                day.slices().stream()
                        .filter(
                                ClassificationSlice::overtime
                        )
                        .map(slice ->
                                new OvertimeProvenanceDraft(
                                        slice.sourceActualWorkIntervalId(),
                                        slice.start(),
                                        slice.end(),
                                        slice.startInstant(),
                                        slice.endInstant(),
                                        slice.sourceTimezone(),
                                        slice.minutes(),
                                        slice.night(),
                                        slice.holiday(),
                                        slice.workedOrdinalStartMinutes()
                                )
                        )
                        .toList();

        int provenanceMinutes =
                provenanceDrafts.stream()
                        .mapToInt(
                                OvertimeProvenanceDraft::minutes
                        )
                        .sum();

        if (provenanceMinutes != targetMinutes) {
            throw new IllegalStateException(
                    "Classified OVERTIME slices total "
                            + provenanceMinutes
                            + " minutes but day total is "
                            + targetMinutes
            );
        }

        String reason =
                "DutyLog · классификация фактической работы "
                        + day.workedMinutes()
                        + " мин; обычный порог "
                        + ordinaryThresholdMinutes
                        + " мин; переработка "
                        + targetMinutes
                        + " мин"
                        + (day.holiday()
                        ? "; праздничный день"
                        : "");

        /*
         * Reconcile the bank first. If used-credit protection or period rules
         * reject the change, provenance is never touched.
         *
         * All calls participate in this outer transaction, so a subsequent
         * provenance failure also rolls the bank change back atomically.
         */
        if (historicalCorrection) {
            overtime.reconcileActualWorkCreditHistoricalCorrection(
                    user,
                    date,
                    targetMinutes,
                    ordinaryThresholdMinutes,
                    reason
            );
        } else {
            overtime.reconcileActualWorkCredit(
                    user,
                    date,
                    targetMinutes,
                    ordinaryThresholdMinutes,
                    reason
            );
        }

        provenance.replaceSystemActualWorkProvenance(
                user,
                date,
                provenanceDrafts
        );
    }
}
