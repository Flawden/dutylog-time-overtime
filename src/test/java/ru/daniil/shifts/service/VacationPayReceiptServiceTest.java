package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayReceipt;
import ru.daniil.shifts.model.VacationPayCalculationSnapshot;
import ru.daniil.shifts.repo.AbsencePeriodRepository;
import ru.daniil.shifts.repo.VacationPayReceiptRepository;
import ru.daniil.shifts.repo.VacationPayCalculationSnapshotRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationPayReceiptServiceTest {

    private static final Long ABSENCE_ID = 77L;
    private static final LocalDate EVENT = LocalDate.of(2026, 9, 15);
    private static final LocalDate RECEIVED = LocalDate.of(2026, 9, 10);
    private static final YearMonth DISCOVERY = YearMonth.of(2026, 8);
    private static final List<YearMonth> PROOFS = List.of(YearMonth.of(2025, 11));

    @Mock private AbsencePeriodRepository absences;
    @Mock private VacationPayReceiptRepository receipts;
    @Mock private VacationPayCalculationSnapshotRepository snapshots;
    @Mock private VacationPayableDaysFactService payableDaysService;
    @Mock private VacationPayApplicationService calculator;
    @Mock private AppUser user;
    @Mock private AppUser otherOwner;
    @Mock private AbsencePeriod absence;
    @Mock private AbsenceType type;
    @Mock private VacationPayableDaysFactService.Resolution payable;
    @Mock private VacationPayApplicationService.Resolution calculation;
    @Mock private VacationPayOrchestrator.Resolution orchestration;
    @Mock private VacationPayMoneyFormula.Resolution money;
    @Mock private VacationAverageUnifiedDailyResolver.Resolution daily;

    private VacationPayReceiptService service;

    @BeforeEach
    void setUp() {
        service = new VacationPayReceiptService(
                absences,
                receipts,
                snapshots,
                payableDaysService,
                calculator,
                new ObjectMapper()
        );
        when(user.getId()).thenReturn(123L);
    }

    @Test
    void minimalReceiptCanBeRecordedWithoutRunningVacationCalculation() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        when(payable.ready()).thenReturn(true);
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto result = service.register(
                user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", null, null
        );

        ArgumentCaptor<VacationPayReceipt> captor = ArgumentCaptor.forClass(VacationPayReceipt.class);
        verify(receipts).saveAndFlush(captor.capture());
        VacationPayReceipt saved = captor.getValue();

        assertEquals(48_000L, saved.getActualReceivedAmountMinor());
        assertEquals("RUB", saved.getCurrencyCode());
        assertNull(saved.getCalculationSnapshot());
        assertEquals(48_000L, result.actualReceivedAmountMinor());
        assertEquals(VacationPayCalculationSnapshot.CALCULATION_NOT_REQUESTED, result.calculationStatus());
        assertNull(result.calculatedGrossAmountMinor());
        assertNull(result.calculationCurrencyCode());
        assertNull(result.calculationAndReceiptCurrencyCodesMatch());
        assertNull(result.calculationSnapshot());
        verifyNoInteractions(calculator);
    }

    @Test
    void readyCalculationIsFrozenSeparatelyFromActualReceipt() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        stubReadyCalculation(50_000L);
        when(snapshots.saveAndFlush(any(VacationPayCalculationSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto result = service.register(
                user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", DISCOVERY, PROOFS
        );

        ArgumentCaptor<VacationPayReceipt> captor = ArgumentCaptor.forClass(VacationPayReceipt.class);
        verify(receipts).saveAndFlush(captor.capture());
        VacationPayReceipt saved = captor.getValue();

        assertEquals(48_000L, saved.getActualReceivedAmountMinor());
        assertEquals(50_000L, saved.getCalculationSnapshot().getCalculatedGrossAmountMinor());
        assertEquals("RUB", saved.getCurrencyCode());
        assertEquals("RUB", saved.getCalculationSnapshot().getCalculationCurrencyCode());
        assertEquals("VACATION", saved.getAbsenceTypeSystemCode());
        assertEquals("VACATION_DAYS", saved.getAbsenceTypeBalancePolicy());
        assertEquals("FULL_DAY", saved.getAbsenceCoverage());
        assertEquals("VACATION_ALLOWANCE", saved.getAbsenceCompensationPolicy());
        assertEquals(VacationPayCalculationSnapshot.CALCULATION_READY, result.calculationStatus());
        assertEquals(48_000L, result.actualReceivedAmountMinor());
        assertEquals(50_000L, result.calculatedGrossAmountMinor());
        assertEquals("RUB", result.calculationCurrencyCode());
        assertTrue(result.calculationAndReceiptCurrencyCodesMatch());
        assertEquals(5, result.payableCalendarDays());
        assertEquals("PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE",
                result.calculationSnapshot().evidence().selectedBasis());
        assertEquals(List.of("2025-11"),
                result.calculationSnapshot().evidence().provenNoPayrollMonths());
        assertEquals("2026-09-15", result.calculationSnapshot().eventDate());
    }

    @Test
    void laterMinimalTransferReusesTheLatestImmutableCalculationSnapshot() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        stubReadyCalculation(50_000L);
        when(snapshots.saveAndFlush(any(VacationPayCalculationSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto first = service.register(
                user, ABSENCE_ID, RECEIVED, 30_000L, "RUB", DISCOVERY, PROOFS
        );
        // Reuse the persisted snapshot returned by the first receipt through the repository.
        ArgumentCaptor<VacationPayReceipt> firstCaptor = ArgumentCaptor.forClass(VacationPayReceipt.class);
        verify(receipts).saveAndFlush(firstCaptor.capture());
        VacationPayCalculationSnapshot sharedSnapshot = firstCaptor.getValue().getCalculationSnapshot();
        when(snapshots.findFirstByOwnerAndAbsencePeriod_IdOrderByIdDesc(user, ABSENCE_ID))
                .thenReturn(Optional.of(sharedSnapshot));

        VacationPayReceiptDto second = service.register(
                user, ABSENCE_ID, RECEIVED.plusDays(1), 18_000L, "RUB", null, null
        );

        assertEquals(50_000L, first.calculatedGrossAmountMinor());
        assertEquals(50_000L, second.calculatedGrossAmountMinor());
        assertEquals(first.calculationSnapshot(), second.calculationSnapshot());
        assertEquals(18_000L, second.actualReceivedAmountMinor());
        verify(snapshots).saveAndFlush(any(VacationPayCalculationSnapshot.class));
        verify(receipts, org.mockito.Mockito.times(2)).saveAndFlush(any(VacationPayReceipt.class));
    }

    @Test
    void differentCalculationCurrencyIsPreservedAndCurrencyCodesAreMarkedDifferent() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        stubReadyCalculation(50_000L);
        when(snapshots.saveAndFlush(any(VacationPayCalculationSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto result = service.register(
                user, ABSENCE_ID, RECEIVED, 48_000L, "USD", DISCOVERY, PROOFS
        );

        assertEquals(48_000L, result.actualReceivedAmountMinor());
        assertEquals("USD", result.currencyCode());
        assertEquals(50_000L, result.calculatedGrossAmountMinor());
        assertEquals("RUB", result.calculationCurrencyCode());
        assertFalse(result.calculationAndReceiptCurrencyCodesMatch());
    }

    @Test
    void blockedCalculationDoesNotPreventRecordingActualMoney() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        when(payable.ready()).thenReturn(true);
        when(calculator.resolve(user, EVENT, ABSENCE_ID, DISCOVERY, PROOFS)).thenReturn(calculation);
        when(calculation.ready()).thenReturn(false);
        when(calculation.blockingStage()).thenReturn(VacationPayOrchestrator.BlockingStage.DAILY_AUTHORITY);
        when(calculation.blockingReason()).thenReturn("AVERAGE_EARNINGS_SOURCE_INCOMPLETE");
        when(calculation.upstreamBlockingReason()).thenReturn("PAYROLL_HISTORY_INCOMPLETE");
        when(calculation.eventDate()).thenReturn(EVENT);
        when(calculation.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(snapshots.saveAndFlush(any(VacationPayCalculationSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto result = service.register(
                user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", DISCOVERY, PROOFS
        );

        assertEquals(48_000L, result.actualReceivedAmountMinor());
        assertEquals(VacationPayCalculationSnapshot.CALCULATION_BLOCKED, result.calculationStatus());
        assertNull(result.calculatedGrossAmountMinor());
        assertNull(result.calculationCurrencyCode());
        assertNull(result.calculationAndReceiptCurrencyCodesMatch());
        assertEquals("AVERAGE_EARNINGS_SOURCE_INCOMPLETE", result.calculationBlockingReason());
        assertNull(result.calculationSnapshot().evidence());
        assertEquals("PAYROLL_HISTORY_INCOMPLETE",
                result.calculationSnapshot().upstreamBlockingReason());
    }

    @Test
    void multipleIncomingTransfersForSameAbsenceAreAllowed() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        when(payable.ready()).thenReturn(true);
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto first = service.register(
                user, ABSENCE_ID, RECEIVED, 30_000L, "RUB", null, null
        );
        VacationPayReceiptDto second = service.register(
                user, ABSENCE_ID, RECEIVED.plusDays(1), 18_000L, "RUB", null, null
        );

        assertEquals(30_000L, first.actualReceivedAmountMinor());
        assertEquals(18_000L, second.actualReceivedAmountMinor());
        verify(receipts, org.mockito.Mockito.times(2)).saveAndFlush(any(VacationPayReceipt.class));
        verifyNoInteractions(calculator);
    }

    @Test
    void foreignAbsenceIsNotFoundAndNeverReachesVacationAuthorities() {
        when(absences.findByIdAndOwner(ABSENCE_ID, user)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.register(user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", null, null)
        );

        assertEquals("NOT_FOUND", ex.getCode());
        verifyNoInteractions(calculator, payableDaysService, receipts, snapshots);
    }

    @Test
    void nonVacationAbsenceIsRejected() {
        stubAbsence();
        when(payable.ready()).thenReturn(false);
        when(payable.blockingReason()).thenReturn(
                VacationPayableDaysFactService.ANNUAL_PAID_VACATION_REQUIRED
        );

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.register(user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", null, null)
        );
        assertEquals("VACATION_PAY_RECEIPT_VACATION_NOT_ELIGIBLE", ex.getCode());
        verify(receipts, never()).saveAndFlush(any(VacationPayReceipt.class));
    }

    @Test
    void absenceTypeOwnedByAnotherUserIsRejected() {
        stubAbsence();
        when(payable.ready()).thenReturn(true);
        when(absence.getType()).thenReturn(type);
        when(type.getOwner()).thenReturn(otherOwner);
        when(otherOwner.getId()).thenReturn(999L);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.register(user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", null, null)
        );

        assertEquals("VACATION_PAY_RECEIPT_ABSENCE_TYPE_OWNERSHIP_MISMATCH", ex.getCode());
        verify(receipts, never()).saveAndFlush(any(VacationPayReceipt.class));
    }

    @Test
    void holidayAuthorityGapDoesNotPreventMinimalReceipt() {
        stubAbsence();
        stubCanonicalAbsenceFields();
        when(payable.ready()).thenReturn(false);
        when(payable.blockingReason()).thenReturn(
                VacationPayableDaysFactService.HOLIDAY_AUTHORITY_UNAVAILABLE
        );
        when(receipts.saveAndFlush(any(VacationPayReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VacationPayReceiptDto result = service.register(
                user, ABSENCE_ID, RECEIVED, 48_000L, "RUB", null, null
        );

        assertEquals(48_000L, result.actualReceivedAmountMinor());
        assertEquals(VacationPayCalculationSnapshot.CALCULATION_NOT_REQUESTED, result.calculationStatus());
    }

    private void stubAbsence() {
        when(absences.findByIdAndOwner(ABSENCE_ID, user)).thenReturn(Optional.of(absence));
        when(absence.getId()).thenReturn(ABSENCE_ID);
        when(absence.getStartDate()).thenReturn(EVENT);
        when(absence.getEndDate()).thenReturn(EVENT.plusDays(4));
        when(payableDaysService.resolve(user, EVENT, ABSENCE_ID)).thenReturn(payable);
    }

    private void stubCanonicalAbsenceFields() {
        when(absence.getStatus()).thenReturn("APPROVED");
        when(absence.getType()).thenReturn(type);
        when(type.getOwner()).thenReturn(user);
        when(type.getSystemCode()).thenReturn("VACATION");
        when(type.getBalancePolicy()).thenReturn("VACATION_DAYS");
        when(absence.getCoverage()).thenReturn("FULL_DAY");
        when(absence.getCompensationPolicy()).thenReturn("VACATION_ALLOWANCE");
    }

    private void stubReadyCalculation(long calculatedGrossMinor) {
        when(payable.ready()).thenReturn(true);
        when(calculator.resolve(user, EVENT, ABSENCE_ID, DISCOVERY, PROOFS)).thenReturn(calculation);
        when(calculation.ready()).thenReturn(true);
        when(calculation.eventDate()).thenReturn(EVENT);
        when(calculation.eventMonth()).thenReturn(YearMonth.from(EVENT));
        when(calculation.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(calculation.discoveryThroughMonth()).thenReturn(DISCOVERY);
        when(calculation.provenNoPayrollMonths()).thenReturn(PROOFS);
        when(calculation.selectedBasis()).thenReturn(
                AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        when(calculation.vacationPay()).thenReturn(orchestration);

        when(orchestration.ready()).thenReturn(true);
        when(orchestration.moneyAuthority()).thenReturn(money);

        when(money.ready()).thenReturn(true);
        when(money.currencyCode()).thenReturn("RUB");
        when(money.vacationPayMinor()).thenReturn(calculatedGrossMinor);
        when(money.averageDaily()).thenReturn(
                new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                        BigInteger.valueOf(calculatedGrossMinor / 5L), BigInteger.ONE
                )
        );
        when(money.exactVacationPay()).thenReturn(
                new VacationPayMoneyFormula.ExactVacationPay(
                        BigInteger.valueOf(calculatedGrossMinor), BigInteger.ONE
                )
        );
        when(money.roundingPolicy()).thenReturn(VacationPayMoneyFormula.ROUNDING_POLICY);
        when(money.payableCalendarDays()).thenReturn(5);
        when(money.dailyAuthority()).thenReturn(daily);
        when(money.payableDaysAuthority()).thenReturn(payable);

        when(daily.ready()).thenReturn(true);
        when(daily.referenceCalculation()).thenReturn(null);
        when(daily.paragraph7CalendarBasis()).thenReturn(
                VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        EVENT, 10, "TEST_P7_AUTHORITY"
                )
        );
        when(daily.paragraph8FormulaBasis()).thenReturn(null);

        List<LocalDate> dates = List.of(
                EVENT, EVENT.plusDays(1), EVENT.plusDays(2), EVENT.plusDays(3), EVENT.plusDays(4)
        );
        when(payable.absencePeriodId()).thenReturn(ABSENCE_ID);
        when(payable.vacationFrom()).thenReturn(EVENT);
        when(payable.vacationTo()).thenReturn(EVENT.plusDays(4));
        when(payable.status()).thenReturn("APPROVED");
        when(payable.payableCalendarDays()).thenReturn(5);
        when(payable.physicalSpanDates()).thenReturn(dates);
        when(payable.payableDates()).thenReturn(dates);
        when(payable.excludedHolidayDates()).thenReturn(List.of());
        when(payable.holidayFacts()).thenReturn(dates.stream()
                .map(date -> new AnnualPaidVacationHolidayPolicy.HolidayFact(
                        date,
                        false,
                        AnnualPaidVacationHolidayPolicy.PRODUCTION_CALENDAR_NON_HOLIDAY_AUTHORITY,
                        "WORKDAY"
                ))
                .toList());
    }
}
