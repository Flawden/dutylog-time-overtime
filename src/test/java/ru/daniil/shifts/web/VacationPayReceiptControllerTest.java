package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptCalculationRequest;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptCreateRequest;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AdjustmentDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AbsenceSummaryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CancelRequest;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionRequest;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionResultDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CurrencySummaryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.HistoryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.RefundRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.VacationPayReceiptLedgerService;
import ru.daniil.shifts.service.VacationPayReceiptService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacationPayReceiptControllerTest {

    @Mock private CurrentUserService users;
    @Mock private ModuleService modules;
    @Mock private VacationPayReceiptService receipts;
    @Mock private VacationPayReceiptLedgerService ledger;
    @Mock private AppUser user;

    private final Principal principal = () -> "receipt-http-user";
    private VacationPayReceiptController controller;

    @BeforeEach
    void setUp() {
        controller = new VacationPayReceiptController(users, modules, receipts, ledger);
    }

    @Test
    void minimalRegistrationDoesNotRequireCalculationInputs() {
        when(users.requireUser(principal)).thenReturn(user);
        VacationPayReceiptDto expected = minimalReceipt();
        when(receipts.register(
                user,
                77L,
                LocalDate.of(2026, 9, 10),
                48_000L,
                "RUB",
                null,
                null
        )).thenReturn(expected);

        VacationPayReceiptCreateRequest request = new VacationPayReceiptCreateRequest(
                77L,
                "2026-09-10",
                48_000L,
                "RUB",
                null
        );

        var response = controller.register(request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(expected, response.getBody());
        verify(users).requireUser(principal);
        verify(modules).requireEnabled(user, ModuleService.PAYROLL);
        verify(receipts).register(
                user,
                77L,
                LocalDate.of(2026, 9, 10),
                48_000L,
                "RUB",
                null,
                null
        );
    }

    @Test
    void optionalCalculationInputsAreParsedAndPassedToServerCalculator() {
        when(users.requireUser(principal)).thenReturn(user);
        VacationPayReceiptDto expected = minimalReceipt();
        when(receipts.register(
                user,
                77L,
                LocalDate.of(2026, 9, 10),
                48_000L,
                "RUB",
                YearMonth.of(2026, 8),
                List.of(YearMonth.of(2025, 11))
        )).thenReturn(expected);

        VacationPayReceiptCreateRequest request = new VacationPayReceiptCreateRequest(
                77L,
                "2026-09-10",
                48_000L,
                "RUB",
                new VacationPayReceiptCalculationRequest("2026-08", List.of("2025-11"))
        );

        var response = controller.register(request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(receipts).register(
                user,
                77L,
                LocalDate.of(2026, 9, 10),
                48_000L,
                "RUB",
                YearMonth.of(2026, 8),
                List.of(YearMonth.of(2025, 11))
        );
    }

    @Test
    void invalidReceivedDateIsRejectedBeforeServiceCall() {
        when(users.requireUser(principal)).thenReturn(user);
        VacationPayReceiptCreateRequest request = new VacationPayReceiptCreateRequest(
                77L, "2026-02-30", 48_000L, "RUB", null
        );

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.register(request, principal)
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verifyNoInteractions(receipts, ledger);
    }

    @Test
    void missingBodyIsRejected() {
        when(users.requireUser(principal)).thenReturn(user);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.register(null, principal)
        );

        assertEquals("BAD_REQUEST", ex.getCode());
        verifyNoInteractions(receipts, ledger);
    }

    @Test
    void disabledPayrollModuleShortCircuitsReceiptOperations() {
        when(users.requireUser(principal)).thenReturn(user);
        doThrow(ApiException.forbidden("MODULE_DISABLED:payroll"))
                .when(modules)
                .requireEnabled(user, ModuleService.PAYROLL);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> controller.list(principal)
        );

        assertEquals("MODULE_DISABLED", ex.getCode());
        verifyNoInteractions(receipts, ledger);
    }

    @Test
    void listReturnsOnlyAuthenticatedUsersServiceProjection() {
        when(users.requireUser(principal)).thenReturn(user);
        VacationPayReceiptDto receipt = minimalReceipt();
        when(receipts.list(user)).thenReturn(List.of(receipt));

        var response = controller.list(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(List.of(receipt), response.getBody());
        verify(modules).requireEnabled(user, ModuleService.PAYROLL);
        verify(receipts).list(user);
    }

    @Test
    void summaryReturnsCurrencySeparatedAbsenceTotals() {
        when(users.requireUser(principal)).thenReturn(user);
        AbsenceSummaryDto summary = new AbsenceSummaryDto(
                77L,
                "2026-09-15",
                "2026-09-19",
                List.of(new CurrencySummaryDto("RUB", "48000", "0", "48000")),
                null
        );
        when(ledger.summary(user)).thenReturn(List.of(summary));

        var response = controller.summary(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(summary), response.getBody());
        verify(ledger).summary(user);
    }

    @Test
    void cancellationCreatesAnAppendOnlyVoidEvent() {
        when(users.requireUser(principal)).thenReturn(user);
        AdjustmentDto expected = new AdjustmentDto(
                5L, 10L, "VOID", null, null, null,
                "Ошибочно внесено", null, "2026-11-08T10:00:00Z"
        );
        when(ledger.cancel(user, 10L, "Ошибочно внесено")).thenReturn(expected);

        var response = controller.cancel(10L, new CancelRequest("Ошибочно внесено"), principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(ledger).cancel(user, 10L, "Ошибочно внесено");
    }

    @Test
    void correctionParsesReplacementTransferAndReason() {
        when(users.requireUser(principal)).thenReturn(user);
        CorrectionResultDto expected = new CorrectionResultDto(
                minimalReceipt(),
                minimalReceipt(),
                new AdjustmentDto(5L, 10L, "VOID", null, null, null,
                        "Неверная сумма", 11L, "2026-11-08T10:00:00Z")
        );
        when(ledger.correct(
                user, 10L, LocalDate.of(2026, 11, 8), 46_000L,
                "RUB", "Неверная сумма", null, null
        )).thenReturn(expected);

        CorrectionRequest request = new CorrectionRequest(
                "2026-11-08", 46_000L, "RUB", "Неверная сумма", null
        );

        var response = controller.correct(10L, request, principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(ledger).correct(
                user, 10L, LocalDate.of(2026, 11, 8), 46_000L,
                "RUB", "Неверная сумма", null, null
        );
    }

    @Test
    void refundCreatesAnOutgoingCashEvent() {
        when(users.requireUser(principal)).thenReturn(user);
        AdjustmentDto expected = new AdjustmentDto(
                6L, 10L, "REFUND", "5000", "RUB", "2026-11-08",
                "Возврат переплаты", null, "2026-11-08T10:00:00Z"
        );
        when(ledger.refund(
                user, 10L, LocalDate.of(2026, 11, 8), 5_000L, "Возврат переплаты"
        )).thenReturn(expected);

        var response = controller.refund(
                10L,
                new RefundRequest("2026-11-08", 5_000L, "Возврат переплаты"),
                principal
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(ledger).refund(
                user, 10L, LocalDate.of(2026, 11, 8), 5_000L, "Возврат переплаты"
        );
    }

    @Test
    void receiptHistoryReturnsAdjustmentEvents() {
        when(users.requireUser(principal)).thenReturn(user);
        HistoryDto expected = new HistoryDto(
                minimalReceipt(),
                List.of(),
                "ACTIVE",
                "0",
                "48000"
        );
        when(ledger.history(user, 10L)).thenReturn(expected);

        var response = controller.history(10L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(ledger).history(user, 10L);
    }

    private static VacationPayReceiptDto minimalReceipt() {
        return new VacationPayReceiptDto(
                1L,
                77L,
                "2026-09-15",
                "2026-09-15",
                "2026-09-19",
                "2026-09-10",
                48_000L,
                "RUB",
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
                "2026-09-10T10:00:00Z",
                null
        );
    }
}
