package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationEngine.NightWindow;
import ru.daniil.shifts.service.PayClassificationService.DayClassification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkdayDerivedClassificationReconciliationTest {

    private final PayClassificationService classification =
            mock(PayClassificationService.class);

    private final OvertimeService overtime =
            mock(OvertimeService.class);

    private final OvertimeCreditProvenanceService provenance =
            mock(OvertimeCreditProvenanceService.class);

    private final WorkdayDerivedCompensationService service =
            new WorkdayDerivedCompensationService(
                    classification,
                    overtime,
                    provenance
            );

    private final AppUser user =
            new AppUser(
                    "derived-classification",
                    "{noop}irrelevant"
            );

    private final NightWindow night =
            new NightWindow(
                    LocalTime.of(22, 0),
                    LocalTime.of(6, 0)
            );

    @Test
    void holidayClassificationProjectsOnlyClassifiedOvertimeIntoTimeBankAndProvenance() {
        LocalDate date =
                LocalDate.of(2026, 8, 18);

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                day(
                        date,
                        480,
                        true,
                        600,
                        480,
                        0,
                        600,
                        120
                )
        );

        service.reconcile(
                user,
                date
        );

        verify(overtime)
                .reconcileActualWorkCredit(
                        eq(user),
                        eq(date),
                        eq(120),
                        eq(480),
                        contains("переработка 120 мин")
                );

        verify(provenance)
                .replaceSystemActualWorkProvenance(
                        eq(user),
                        eq(date),
                        argThat(drafts ->
                                drafts.size() == 1
                                        && drafts.get(0).minutes() == 120
                                        && drafts.get(0).sourceActualWorkIntervalId() == 999L
                                        && drafts.get(0).holiday()
                                        && drafts.get(0).overtimeOrdinalStartMinutes() == 480
                        )
                );

        verify(
                overtime,
                never()
        ).reconcileActualWorkCreditHistoricalCorrection(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyString()
        );
    }

    @Test
    void zeroOrdinaryThresholdProjectsAllClassifiedFactAsOvertime() {
        LocalDate date =
                LocalDate.of(2026, 8, 19);

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                day(
                        date,
                        0,
                        true,
                        240,
                        0,
                        0,
                        240,
                        240
                )
        );

        service.reconcile(
                user,
                date
        );

        verify(overtime)
                .reconcileActualWorkCredit(
                        eq(user),
                        eq(date),
                        eq(240),
                        eq(0),
                        contains("переработка 240 мин")
                );

        verify(provenance)
                .replaceSystemActualWorkProvenance(
                        eq(user),
                        eq(date),
                        argThat(drafts ->
                                drafts.size() == 1
                                        && drafts.get(0).minutes() == 240
                                        && drafts.get(0).overtimeOrdinalStartMinutes() == 0
                        )
                );
    }

    @Test
    void zeroOvertimeStillReconcilesAndClearsProvenanceProjection() {
        LocalDate date =
                LocalDate.of(2026, 8, 20);

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                day(
                        date,
                        480,
                        false,
                        480,
                        480,
                        0,
                        0,
                        0
                )
        );

        service.reconcile(
                user,
                date
        );

        verify(overtime)
                .reconcileActualWorkCredit(
                        eq(user),
                        eq(date),
                        eq(0),
                        eq(480),
                        anyString()
                );

        verify(provenance)
                .replaceSystemActualWorkProvenance(
                        eq(user),
                        eq(date),
                        argThat(List::isEmpty)
                );
    }

    @Test
    void historicalCorrectionUsesHistoricalBankPathThenRebuildsProvenance() {
        LocalDate date =
                LocalDate.of(2026, 3, 29);

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                day(
                        date,
                        0,
                        false,
                        420,
                        0,
                        300,
                        0,
                        420
                )
        );

        service.reconcileRangeHistoricalCorrection(
                user,
                date,
                date
        );

        verify(overtime)
                .reconcileActualWorkCreditHistoricalCorrection(
                        eq(user),
                        eq(date),
                        eq(420),
                        eq(0),
                        contains("переработка 420 мин")
                );

        verify(provenance)
                .replaceSystemActualWorkProvenance(
                        eq(user),
                        eq(date),
                        argThat(drafts ->
                                drafts.size() == 1
                                        && drafts.get(0).minutes() == 420
                        )
                );

        verify(
                overtime,
                never()
        ).reconcileActualWorkCredit(
                any(),
                any(),
                anyInt(),
                anyInt(),
                anyString()
        );
    }

    private DayClassification day(
            LocalDate date,
            int ordinaryThreshold,
            boolean holiday,
            int worked,
            int regular,
            int nightMinutes,
            int holidayMinutes,
            int overtimeMinutes
    ) {
        List<ClassificationSlice> slices =
                overtimeMinutes <= 0
                        ? List.of()
                        : List.of(
                                new ClassificationSlice(
                                        date.atStartOfDay()
                                                .plusMinutes(
                                                        ordinaryThreshold
                                                ),
                                        date.atStartOfDay()
                                                .plusMinutes(
                                                        ordinaryThreshold
                                                                + overtimeMinutes
                                                ),
                                        null,
                                        null,
                                        null,
                                        999L,
                                        ordinaryThreshold,
                                        overtimeMinutes,
                                        false,
                                        nightMinutes > 0,
                                        holiday,
                                        true
                                )
                        );

        return new DayClassification(
                date,
                ordinaryThreshold,
                holiday,
                night,
                worked,
                regular,
                nightMinutes,
                holidayMinutes,
                overtimeMinutes,
                slices
        );
    }
}
