package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptCalculationRequest;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptCreateRequest;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CancelRequest;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionRequest;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionResultDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AdjustmentDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AbsenceSummaryDto;
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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;


@RestController
@RequestMapping({
        "/api/payroll/vacation-pay/receipts",
        "/api/v1/payroll/vacation-pay/receipts"
})
public class VacationPayReceiptController {

    private final CurrentUserService users;
    private final ModuleService modules;
    private final VacationPayReceiptService receipts;
    private final VacationPayReceiptLedgerService ledger;

    public VacationPayReceiptController(
            CurrentUserService users,
            ModuleService modules,
            VacationPayReceiptService receipts,
            VacationPayReceiptLedgerService ledger
    ) {
        this.users = Objects.requireNonNull(users, "Vacation receipt HTTP requires current-user service");
        this.modules = Objects.requireNonNull(modules, "Vacation receipt HTTP requires module service");
        this.receipts = Objects.requireNonNull(receipts, "Vacation receipt HTTP requires receipt service");
        this.ledger = Objects.requireNonNull(ledger, "Vacation receipt ledger service is required");
    }

    @PostMapping
    public ResponseEntity<VacationPayReceiptDto> register(
            @Valid @RequestBody(required = false) VacationPayReceiptCreateRequest request,
            Principal principal
    ) {
        AppUser user = user(principal);
        if (request == null) {
            throw ApiException.badRequest("Тело запроса регистрации отпускных обязательно");
        }
        if (request.absencePeriodId() == null || request.absencePeriodId() <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_ABSENCE_REQUIRED",
                    "Для регистрации отпускных необходимо выбрать отпуск"
            );
        }
        if (request.actualReceivedAmountMinor() == null || request.actualReceivedAmountMinor() <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_AMOUNT_INVALID",
                    "Фактически полученная сумма должна быть положительной"
            );
        }

        LocalDate receivedDate = parseDate(
                request.receivedDate(),
                "receivedDate должен быть в формате yyyy-MM-dd"
        );
        String currencyCode = requireCurrency(request.currencyCode());

        CalculationInputs calculation = parseCalculation(request.calculation());
        VacationPayReceiptDto result = receipts.register(
                user,
                request.absencePeriodId(),
                receivedDate,
                request.actualReceivedAmountMinor(),
                currencyCode,
                calculation.discoveryThroughMonth(),
                calculation.provenNoPayrollMonths()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @GetMapping
    public ResponseEntity<List<VacationPayReceiptDto>> list(Principal principal) {
        AppUser user = user(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(receipts.list(user));
    }

    @GetMapping("/summary")
    public ResponseEntity<List<AbsenceSummaryDto>> summary(Principal principal) {
        AppUser user = user(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ledger.summary(user));
    }

    @GetMapping("/{receiptId}/history")
    public ResponseEntity<HistoryDto> history(
            @PathVariable Long receiptId,
            Principal principal
    ) {
        AppUser user = user(principal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ledger.history(user, receiptId));
    }

    @PostMapping("/{receiptId}/cancel")
    public ResponseEntity<AdjustmentDto> cancel(
            @PathVariable Long receiptId,
            @Valid @RequestBody(required = false) CancelRequest request,
            Principal principal
    ) {
        AppUser user = user(principal);
        if (request == null) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_ADJUSTMENT_REASON_REQUIRED",
                    "Тело запроса отмены обязательно"
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ledger.cancel(user, receiptId, request.reason()));
    }

    @PostMapping("/{receiptId}/correct")
    public ResponseEntity<CorrectionResultDto> correct(
            @PathVariable Long receiptId,
            @Valid @RequestBody(required = false) CorrectionRequest request,
            Principal principal
    ) {
        AppUser user = user(principal);
        if (request == null) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_CORRECTION_BODY_REQUIRED",
                    "Тело запроса исправления обязательно"
            );
        }
        if (request.actualReceivedAmountMinor() == null || request.actualReceivedAmountMinor() <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_AMOUNT_INVALID",
                    "Фактически полученная сумма должна быть положительной"
            );
        }
        LocalDate receivedDate = parseDate(
                request.receivedDate(),
                "receivedDate должен быть в формате yyyy-MM-dd"
        );
        String currencyCode = requireCurrency(request.currencyCode());
        CalculationInputs calculation = parseCalculation(request.calculation());

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ledger.correct(
                        user,
                        receiptId,
                        receivedDate,
                        request.actualReceivedAmountMinor(),
                        currencyCode,
                        request.reason(),
                        calculation.discoveryThroughMonth(),
                        calculation.provenNoPayrollMonths()
                ));
    }

    @PostMapping("/{receiptId}/refund")
    public ResponseEntity<AdjustmentDto> refund(
            @PathVariable Long receiptId,
            @Valid @RequestBody(required = false) RefundRequest request,
            Principal principal
    ) {
        AppUser user = user(principal);
        if (request == null) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_REFUND_BODY_REQUIRED",
                    "Тело запроса возврата обязательно"
            );
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_REFUND_AMOUNT_INVALID",
                    "Сумма возврата должна быть положительной"
            );
        }
        LocalDate refundDate = parseDate(
                request.refundDate(),
                "refundDate должен быть в формате yyyy-MM-dd"
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ledger.refund(
                        user,
                        receiptId,
                        refundDate,
                        request.amountMinor(),
                        request.reason()
                ));
    }

    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.PAYROLL);
        return user;
    }

    private static String requireCurrency(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_CURRENCY_INVALID",
                    "Код валюты должен состоять из трёх заглавных букв"
            );
        }
        return currencyCode;
    }

    private static CalculationInputs parseCalculation(VacationPayReceiptCalculationRequest calculation) {
        if (calculation == null) return new CalculationInputs(null, null);
        YearMonth discoveryThroughMonth = parseMonth(
                calculation.discoveryThroughMonth(),
                "discoveryThroughMonth должен быть в формате yyyy-MM"
        );
        List<YearMonth> provenNoPayrollMonths = parseProofMonths(calculation.provenNoPayrollMonths());
        return new CalculationInputs(discoveryThroughMonth, provenNoPayrollMonths);
    }

    private static LocalDate parseDate(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private static YearMonth parseMonth(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private static List<YearMonth> parseProofMonths(List<String> values) {
        if (values == null) {
            throw ApiException.badRequest(
                    "provenNoPayrollMonths должен быть передан явно, даже если список пуст"
            );
        }
        return values.stream()
                .map(value -> parseMonth(
                        value,
                        "Каждый provenNoPayrollMonths должен быть в формате yyyy-MM"
                ))
                .toList();
    }

    private record CalculationInputs(
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
    }
}
