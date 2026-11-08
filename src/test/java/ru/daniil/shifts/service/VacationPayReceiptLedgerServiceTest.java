package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.dto.Dtos.VacationPayCalculationSnapshotDto;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AbsenceSummaryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AdjustmentDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionResultDto;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayReceipt;
import ru.daniil.shifts.model.VacationPayReceiptAdjustment;
import ru.daniil.shifts.repo.VacationPayReceiptAdjustmentRepository;
import ru.daniil.shifts.repo.VacationPayReceiptRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationPayReceiptLedgerServiceTest {

    @Mock private VacationPayReceiptRepository receipts;
    @Mock private VacationPayReceiptAdjustmentRepository adjustments;
    @Mock private VacationPayReceiptService receiptService;
    @Mock private AppUser user;
    @Mock private VacationPayReceipt original;
    @Mock private VacationPayReceipt replacement;
    @Mock private AbsencePeriod absence;

    private VacationPayReceiptLedgerService service;

    @BeforeEach
    void setUp() {
        service = new VacationPayReceiptLedgerService(receipts, adjustments, receiptService);
    }

    @Test
    void cancelAppendsVoidEventWithoutChangingOriginalReceipt() {
        stubLockedReceipt();
        when(user.getId()).thenReturn(123L);
        when(original.getOwner()).thenReturn(user);
        when(adjustments.existsByReceipt_Id(10L)).thenReturn(false);
        when(adjustments.saveAndFlush(any(VacationPayReceiptAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdjustmentDto result = service.cancel(user, 10L, "Ошибочно внесённый перевод");

        assertEquals(10L, result.receiptId());
        assertEquals(VacationPayReceiptAdjustment.TYPE_VOID, result.adjustmentType());
        assertEquals("Ошибочно внесённый перевод", result.reason());
        verify(adjustments).saveAndFlush(any(VacationPayReceiptAdjustment.class));
    }

    @Test
    void alreadyAdjustedReceiptCannotBeCancelledAgain() {
        stubLockedReceipt();
        when(adjustments.existsByReceipt_Id(10L)).thenReturn(true);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.cancel(user, 10L, "Повторная отмена")
        );

        assertEquals("VACATION_PAY_RECEIPT_ALREADY_ADJUSTED", ex.getCode());
        verify(adjustments, never()).saveAndFlush(any(VacationPayReceiptAdjustment.class));
    }

    @Test
    void partialRefundAppendsRealOutgoingCashEvent() {
        stubLockedReceipt();
        when(user.getId()).thenReturn(123L);
        when(original.getOwner()).thenReturn(user);
        when(original.getActualReceivedAmountMinor()).thenReturn(30_000L);
        when(original.getCurrencyCode()).thenReturn("RUB");
        when(adjustments.existsByReceipt_IdAndAdjustmentType(
                10L, VacationPayReceiptAdjustment.TYPE_VOID
        )).thenReturn(false);
        when(adjustments.sumRefundsByReceiptId(10L)).thenReturn(5_000L);
        when(adjustments.saveAndFlush(any(VacationPayReceiptAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdjustmentDto result = service.refund(
                user, 10L, LocalDate.of(2026, 11, 8), 7_000L, "Частичный возврат работодателю"
        );

        assertEquals(VacationPayReceiptAdjustment.TYPE_REFUND, result.adjustmentType());
        assertEquals("7000", result.amountMinor());
        assertEquals("RUB", result.currencyCode());
        assertEquals("2026-11-08", result.eventDate());
    }

    @Test
    void refundCannotExceedRemainingReceiptAmount() {
        stubLockedReceipt();
        when(original.getActualReceivedAmountMinor()).thenReturn(30_000L);
        when(adjustments.existsByReceipt_IdAndAdjustmentType(
                10L, VacationPayReceiptAdjustment.TYPE_VOID
        )).thenReturn(false);
        when(adjustments.sumRefundsByReceiptId(10L)).thenReturn(25_000L);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.refund(
                        user, 10L, LocalDate.of(2026, 11, 8), 6_000L, "Слишком большой возврат"
                )
        );

        assertEquals("VACATION_PAY_RECEIPT_REFUND_EXCEEDS_REMAINING", ex.getCode());
        verify(adjustments, never()).saveAndFlush(any(VacationPayReceiptAdjustment.class));
    }

    @Test
    void correctionCreatesReplacementAndVoidInOneServiceTransaction() {
        stubLockedReceipt();
        when(user.getId()).thenReturn(123L);
        when(original.getOwner()).thenReturn(user);
        when(original.getAbsencePeriod()).thenReturn(absence);
        when(absence.getId()).thenReturn(77L);
        VacationPayReceiptDto originalDto = receiptDto(10L, 30_000L, "RUB");
        VacationPayReceiptDto replacementDto = receiptDto(11L, 28_000L, "RUB");
        when(adjustments.existsByReceipt_Id(10L)).thenReturn(false);
        when(receiptService.register(
                user, 77L, LocalDate.of(2026, 11, 7), 28_000L, "RUB", null, null
        )).thenReturn(replacementDto);
        when(receipts.findByIdAndOwner(11L, user)).thenReturn(Optional.of(replacement));
        when(replacement.getId()).thenReturn(11L);
        when(replacement.getOwner()).thenReturn(user);
        when(replacement.getAbsencePeriod()).thenReturn(absence);
        when(adjustments.saveAndFlush(any(VacationPayReceiptAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptService.get(user, 10L)).thenReturn(originalDto);

        CorrectionResultDto result = service.correct(
                user,
                10L,
                LocalDate.of(2026, 11, 7),
                28_000L,
                "RUB",
                "Исправлена ошибочная сумма",
                null,
                null
        );

        assertEquals(originalDto, result.originalReceipt());
        assertEquals(replacementDto, result.replacementReceipt());
        assertEquals(VacationPayReceiptAdjustment.TYPE_VOID, result.cancellation().adjustmentType());
        assertEquals(11L, result.cancellation().replacementReceiptId());
        verify(receiptService).register(
                user, 77L, LocalDate.of(2026, 11, 7), 28_000L, "RUB", null, null
        );
        verify(adjustments).saveAndFlush(any(VacationPayReceiptAdjustment.class));
    }

    @Test
    void summaryGroupsByAbsenceAndCurrencyAndExcludesVoidedReceipts() {
        VacationPayReceiptDto first = receiptDto(1L, 30_000L, "RUB");
        VacationPayReceiptDto second = receiptDto(2L, 12_000L, "RUB");
        VacationPayReceiptDto third = receiptDto(3L, 6_000L, "USD");
        VacationPayReceiptDto voided = receiptDto(4L, 9_000L, "RUB");

        when(receiptService.list(user)).thenReturn(List.of(first, second, third, voided));

        VacationPayReceiptAdjustment refund = org.mockito.Mockito.mock(VacationPayReceiptAdjustment.class);
        VacationPayReceipt refundReceipt = org.mockito.Mockito.mock(VacationPayReceipt.class);
        when(refund.getReceipt()).thenReturn(refundReceipt);
        when(refundReceipt.getId()).thenReturn(1L);
        when(refund.getAdjustmentType()).thenReturn(VacationPayReceiptAdjustment.TYPE_REFUND);
        when(refund.getAmountMinor()).thenReturn(5_000L);

        VacationPayReceiptAdjustment voidEvent = org.mockito.Mockito.mock(VacationPayReceiptAdjustment.class);
        VacationPayReceipt voidReceipt = org.mockito.Mockito.mock(VacationPayReceipt.class);
        when(voidEvent.getReceipt()).thenReturn(voidReceipt);
        when(voidReceipt.getId()).thenReturn(4L);
        when(voidEvent.getAdjustmentType()).thenReturn(VacationPayReceiptAdjustment.TYPE_VOID);

        when(adjustments.findByReceipt_IdInOrderByCreatedAtAscIdAsc(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(refund, voidEvent));

        List<AbsenceSummaryDto> result = service.summary(user);

        assertEquals(1, result.size());
        assertEquals(77L, result.get(0).absencePeriodId());
        assertEquals(2, result.get(0).currencyTotals().size());
        assertEquals("42000", result.get(0).currencyTotals().get(0).receivedBeforeRefundsAmountMinor());
        assertEquals("5000", result.get(0).currencyTotals().get(0).refundedAmountMinor());
        assertEquals("37000", result.get(0).currencyTotals().get(0).netRetainedAmountMinor());
        assertEquals("USD", result.get(0).currencyTotals().get(1).currencyCode());
        assertEquals("6000", result.get(0).currencyTotals().get(1).netRetainedAmountMinor());
    }

    @Test
    void summaryUsesLatestActiveReceiptSnapshotNotLatestVoidedReceiptSnapshot() {
        VacationPayCalculationSnapshotDto activeSnapshot = new VacationPayCalculationSnapshotDto(
                "VACATION_PAY_RECEIPT_CALCULATION_V1", "BLOCKED", "2026-11-01",
                "2026-10", List.of(), "CALCULATION", "ACTIVE_SNAPSHOT", null, null
        );
        VacationPayCalculationSnapshotDto cancelledSnapshot = new VacationPayCalculationSnapshotDto(
                "VACATION_PAY_RECEIPT_CALCULATION_V1", "BLOCKED", "2026-11-01",
                "2026-10", List.of(), "CALCULATION", "CANCELLED_SNAPSHOT", null, null
        );
        VacationPayReceiptDto active = receiptDto(10L, 30_000L, "RUB", activeSnapshot);
        VacationPayReceiptDto cancelled = receiptDto(11L, 99_000L, "RUB", cancelledSnapshot);
        when(receiptService.list(user)).thenReturn(List.of(active, cancelled));

        VacationPayReceiptAdjustment voidEvent = org.mockito.Mockito.mock(VacationPayReceiptAdjustment.class);
        VacationPayReceipt voidReceipt = org.mockito.Mockito.mock(VacationPayReceipt.class);
        when(voidEvent.getReceipt()).thenReturn(voidReceipt);
        when(voidReceipt.getId()).thenReturn(11L);
        when(voidEvent.getAdjustmentType()).thenReturn(VacationPayReceiptAdjustment.TYPE_VOID);
        when(adjustments.findByReceipt_IdInOrderByCreatedAtAscIdAsc(List.of(10L, 11L)))
                .thenReturn(List.of(voidEvent));

        List<AbsenceSummaryDto> result = service.summary(user);

        assertEquals(1, result.size());
        assertEquals("30000", result.get(0).currencyTotals().get(0).netRetainedAmountMinor());
        assertEquals(activeSnapshot, result.get(0).calculationSnapshot());
    }

    private void stubLockedReceipt() {
        when(receipts.findByIdAndOwnerForUpdate(10L, user)).thenReturn(Optional.of(original));
        when(original.getId()).thenReturn(10L);
    }

    private static VacationPayReceiptDto receiptDto(Long id, long amount, String currency) {
        return receiptDto(id, amount, currency, null);
    }

    private static VacationPayReceiptDto receiptDto(
            Long id, long amount, String currency, VacationPayCalculationSnapshotDto snapshot
    ) {
        return new VacationPayReceiptDto(
                id,
                77L,
                "2026-11-01",
                "2026-11-01",
                "2026-11-05",
                "2026-11-07",
                amount,
                currency,
                null,
                null,
                "APPROVED",
                "VACATION",
                "VACATION_DAYS",
                "FULL_DAY",
                "VACATION_ALLOWANCE",
                "NOT_REQUESTED",
                null,
                null,
                null,
                null,
                "a".repeat(64),
                "2026-11-07T10:00:00Z",
                snapshot
        );
    }
}
