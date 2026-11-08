package ru.daniil.shifts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.daniil.shifts.dto.Dtos.VacationPayCalculationSnapshotDto;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptCalculationRequest;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;

import java.util.List;

/** HTTP contracts for append-only vacation receipt corrections and cash refunds. */
public final class VacationPayReceiptContracts {

    private VacationPayReceiptContracts() {
    }

    public record CancelRequest(
            @NotBlank(message = "Причина отмены обязательна")
            String reason
    ) {
    }

    public record CorrectionRequest(
            @NotBlank(message = "Дата фактического получения обязательна")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Дата должна быть в формате yyyy-MM-dd")
            String receivedDate,

            @NotNull(message = "Фактически полученная сумма обязательна")
            @Min(value = 1, message = "Фактически полученная сумма должна быть положительной")
            Long actualReceivedAmountMinor,

            @NotBlank(message = "Валюта фактического поступления обязательна")
            @Pattern(regexp = "[A-Z]{3}", message = "Код валюты должен состоять из трёх заглавных букв")
            String currencyCode,

            @NotBlank(message = "Причина исправления обязательна")
            String reason,

            @Valid
            VacationPayReceiptCalculationRequest calculation
    ) {
    }

    public record RefundRequest(
            @NotBlank(message = "Дата возврата обязательна")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Дата возврата должна быть в формате yyyy-MM-dd")
            String refundDate,

            @NotNull(message = "Сумма возврата обязательна")
            @Min(value = 1, message = "Сумма возврата должна быть положительной")
            Long amountMinor,

            @NotBlank(message = "Причина возврата обязательна")
            String reason
    ) {
    }

    public record AdjustmentDto(
            Long id,
            Long receiptId,
            String adjustmentType,
            String amountMinor,
            String currencyCode,
            String eventDate,
            String reason,
            Long replacementReceiptId,
            String createdAt
    ) {
    }

    public record HistoryDto(
            VacationPayReceiptDto receipt,
            List<AdjustmentDto> adjustments,
            String status,
            String refundedAmountMinor,
            String remainingAmountMinor
    ) {
    }

    public record CorrectionResultDto(
            VacationPayReceiptDto originalReceipt,
            VacationPayReceiptDto replacementReceipt,
            AdjustmentDto cancellation
    ) {
    }

    public record CurrencySummaryDto(
            String currencyCode,
            String receivedBeforeRefundsAmountMinor,
            String refundedAmountMinor,
            String netRetainedAmountMinor
    ) {
    }

    public record AbsenceSummaryDto(
            Long absencePeriodId,
            String vacationFrom,
            String vacationTo,
            List<CurrencySummaryDto> currencyTotals,
            VacationPayCalculationSnapshotDto calculationSnapshot
    ) {
    }
}
