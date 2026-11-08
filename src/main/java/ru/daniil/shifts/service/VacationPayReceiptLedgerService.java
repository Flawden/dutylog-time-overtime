package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AdjustmentDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.AbsenceSummaryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CorrectionResultDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.CurrencySummaryDto;
import ru.daniil.shifts.dto.VacationPayReceiptContracts.HistoryDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayReceipt;
import ru.daniil.shifts.model.VacationPayReceiptAdjustment;
import ru.daniil.shifts.repo.VacationPayReceiptAdjustmentRepository;
import ru.daniil.shifts.repo.VacationPayReceiptRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Append-only correction, cancellation, refund and per-absence aggregation for vacation receipts.
 */
@Service
public class VacationPayReceiptLedgerService {

    private final VacationPayReceiptRepository receipts;
    private final VacationPayReceiptAdjustmentRepository adjustments;
    private final VacationPayReceiptService receiptService;

    public VacationPayReceiptLedgerService(
            VacationPayReceiptRepository receipts,
            VacationPayReceiptAdjustmentRepository adjustments,
            VacationPayReceiptService receiptService
    ) {
        this.receipts = Objects.requireNonNull(receipts, "Receipt repository is required");
        this.adjustments = Objects.requireNonNull(adjustments, "Receipt adjustment repository is required");
        this.receiptService = Objects.requireNonNull(receiptService, "Receipt service is required");
    }

    @Transactional
    public AdjustmentDto cancel(AppUser user, Long receiptId, String reason) {
        String normalizedReason = requireReason(reason);
        VacationPayReceipt original = lockOwnedReceipt(user, receiptId);
        ensureUnadjusted(original.getId());

        VacationPayReceiptAdjustment event = VacationPayReceiptAdjustment.voidReceipt(
                user, original, normalizedReason, null
        );
        return toDto(adjustments.saveAndFlush(event));
    }

    @Transactional
    public CorrectionResultDto correct(
            AppUser user,
            Long receiptId,
            LocalDate replacementReceivedDate,
            long replacementAmountMinor,
            String replacementCurrencyCode,
            String reason,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        String normalizedReason = requireReason(reason);
        VacationPayReceipt original = lockOwnedReceipt(user, receiptId);
        ensureUnadjusted(original.getId());

        VacationPayReceiptDto replacementDto = receiptService.register(
                user,
                original.getAbsencePeriod().getId(),
                replacementReceivedDate,
                replacementAmountMinor,
                replacementCurrencyCode,
                discoveryThroughMonth,
                provenNoPayrollMonths
        );
        VacationPayReceipt replacement = receipts.findByIdAndOwner(replacementDto.id(), user)
                .orElseThrow(() -> new IllegalStateException("Replacement receipt disappeared inside correction transaction"));

        VacationPayReceiptAdjustment cancellation = VacationPayReceiptAdjustment.voidReceipt(
                user,
                original,
                normalizedReason,
                replacement
        );
        AdjustmentDto cancellationDto = toDto(adjustments.saveAndFlush(cancellation));

        return new CorrectionResultDto(
                receiptService.get(user, original.getId()),
                replacementDto,
                cancellationDto
        );
    }

    @Transactional
    public AdjustmentDto refund(
            AppUser user,
            Long receiptId,
            LocalDate refundDate,
            long amountMinor,
            String reason
    ) {
        String normalizedReason = requireReason(reason);
        if (refundDate == null) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_REFUND_DATE_INVALID",
                    "Дата фактического возврата обязательна"
            );
        }
        if (amountMinor <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_REFUND_AMOUNT_INVALID",
                    "Сумма возврата должна быть положительной"
            );
        }

        VacationPayReceipt original = lockOwnedReceipt(user, receiptId);
        if (adjustments.existsByReceipt_IdAndAdjustmentType(
                original.getId(),
                VacationPayReceiptAdjustment.TYPE_VOID
        )) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_ALREADY_VOIDED",
                    "Нельзя зарегистрировать возврат по аннулированному поступлению"
            );
        }

        long alreadyRefunded = Objects.requireNonNullElse(
                adjustments.sumRefundsByReceiptId(original.getId()),
                0L
        );
        if (amountMinor > original.getActualReceivedAmountMinor() - alreadyRefunded) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_REFUND_EXCEEDS_REMAINING",
                    "Сумма возврата превышает остаток по фактическому поступлению"
            );
        }

        VacationPayReceiptAdjustment event = VacationPayReceiptAdjustment.refund(
                user, original, amountMinor, refundDate, normalizedReason
        );
        return toDto(adjustments.saveAndFlush(event));
    }

    @Transactional(readOnly = true)
    public HistoryDto history(AppUser user, Long receiptId) {
        VacationPayReceiptDto receipt = receiptService.get(user, receiptId);
        List<VacationPayReceiptAdjustment> events =
                adjustments.findByReceipt_IdOrderByCreatedAtAscIdAsc(receiptId);
        return toHistory(receipt, events);
    }

    @Transactional(readOnly = true)
    public List<AbsenceSummaryDto> summary(AppUser user) {
        List<VacationPayReceiptDto> receiptRows = receiptService.list(user);
        if (receiptRows.isEmpty()) return List.of();

        List<Long> receiptIds = receiptRows.stream().map(VacationPayReceiptDto::id).toList();
        List<VacationPayReceiptAdjustment> allEvents =
                adjustments.findByReceipt_IdInOrderByCreatedAtAscIdAsc(receiptIds);

        Map<Long, List<VacationPayReceiptAdjustment>> eventsByReceipt = new HashMap<>();
        for (VacationPayReceiptAdjustment event : allEvents) {
            eventsByReceipt.computeIfAbsent(event.getReceipt().getId(), ignored -> new ArrayList<>())
                    .add(event);
        }

        Map<Long, SummaryAccumulator> byAbsence = new LinkedHashMap<>();
        Map<Long, VacationPayReceiptDto> latestActiveReceiptByAbsence = new HashMap<>();
        for (VacationPayReceiptDto receipt : receiptRows) {
            List<VacationPayReceiptAdjustment> events =
                    eventsByReceipt.getOrDefault(receipt.id(), List.of());
            boolean voided = events.stream().anyMatch(event ->
                    VacationPayReceiptAdjustment.TYPE_VOID.equals(event.getAdjustmentType())
            );
            if (voided) continue;

            // The financial total and its calculation context must both come from
            // active receipts. A newer VOIDed transfer must not replace the
            // snapshot shown alongside the active receipts' totals.
            latestActiveReceiptByAbsence.merge(
                    receipt.absencePeriodId(),
                    receipt,
                    (left, right) -> left.id() >= right.id() ? left : right
            );

            BigInteger refunded = events.stream()
                    .filter(event -> VacationPayReceiptAdjustment.TYPE_REFUND.equals(event.getAdjustmentType()))
                    .map(VacationPayReceiptAdjustment::getAmountMinor)
                    .filter(Objects::nonNull)
                    .map(BigInteger::valueOf)
                    .reduce(BigInteger.ZERO, BigInteger::add);

            SummaryAccumulator absence = byAbsence.computeIfAbsent(
                    receipt.absencePeriodId(),
                    ignored -> new SummaryAccumulator(
                            receipt.absencePeriodId(),
                            receipt.vacationFrom(),
                            receipt.vacationTo()
                    )
            );
            CurrencyAccumulator currency = absence.currencies.computeIfAbsent(
                    receipt.currencyCode(),
                    ignored -> new CurrencyAccumulator()
            );
            BigInteger gross = BigInteger.valueOf(receipt.actualReceivedAmountMinor());
            currency.receivedBeforeRefunds = currency.receivedBeforeRefunds.add(gross);
            currency.refunded = currency.refunded.add(refunded);
            currency.netRetained = currency.netRetained.add(gross.subtract(refunded));
        }

        return byAbsence.values().stream()
                .sorted(Comparator.comparing(SummaryAccumulator::absencePeriodId))
                .map(value -> new AbsenceSummaryDto(
                        value.absencePeriodId,
                        value.vacationFrom,
                        value.vacationTo,
                        value.currencies.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> new CurrencySummaryDto(
                                        entry.getKey(),
                                        entry.getValue().receivedBeforeRefunds.toString(),
                                        entry.getValue().refunded.toString(),
                                        entry.getValue().netRetained.toString()
                                ))
                                .toList(),
                        latestActiveReceiptByAbsence.get(value.absencePeriodId).calculationSnapshot()
                ))
                .toList();
    }

    private HistoryDto toHistory(
            VacationPayReceiptDto receipt,
            List<VacationPayReceiptAdjustment> events
    ) {
        boolean voided = events.stream().anyMatch(event ->
                VacationPayReceiptAdjustment.TYPE_VOID.equals(event.getAdjustmentType())
        );
        BigInteger refunded = events.stream()
                .filter(event -> VacationPayReceiptAdjustment.TYPE_REFUND.equals(event.getAdjustmentType()))
                .map(VacationPayReceiptAdjustment::getAmountMinor)
                .filter(Objects::nonNull)
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);

        BigInteger original = BigInteger.valueOf(receipt.actualReceivedAmountMinor());
        BigInteger remaining = voided ? BigInteger.ZERO : original.subtract(refunded);
        String status;
        if (voided) {
            status = "VOIDED";
        } else if (refunded.signum() == 0) {
            status = "ACTIVE";
        } else if (remaining.signum() == 0) {
            status = "REFUNDED";
        } else {
            status = "PARTIALLY_REFUNDED";
        }

        return new HistoryDto(
                receipt,
                events.stream().map(this::toDto).toList(),
                status,
                refunded.toString(),
                remaining.toString()
        );
    }

    private AdjustmentDto toDto(VacationPayReceiptAdjustment event) {
        return new AdjustmentDto(
                event.getId(),
                event.getReceipt().getId(),
                event.getAdjustmentType(),
                event.getAmountMinor() == null ? null : event.getAmountMinor().toString(),
                event.getCurrencyCode(),
                event.getEventDate() == null ? null : event.getEventDate().toString(),
                event.getReason(),
                event.getReplacementReceipt() == null ? null : event.getReplacementReceipt().getId(),
                event.getCreatedAt().toString()
        );
    }

    private VacationPayReceipt lockOwnedReceipt(AppUser user, Long receiptId) {
        Objects.requireNonNull(user, "Receipt adjustment requires authenticated user");
        if (receiptId == null || receiptId <= 0L) {
            throw ApiException.badRequest("VACATION_PAY_RECEIPT_ID_INVALID", "Некорректный идентификатор поступления");
        }
        return receipts.findByIdAndOwnerForUpdate(receiptId, user)
                .orElseThrow(() -> ApiException.notFound("Поступление не найдено или недоступно текущему пользователю"));
    }

    private void ensureUnadjusted(Long receiptId) {
        if (adjustments.existsByReceipt_Id(receiptId)) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_ALREADY_ADJUSTED",
                    "Поступление уже отменено, исправлено или по нему зарегистрирован возврат"
            );
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_ADJUSTMENT_REASON_REQUIRED",
                    "Причина финансовой корректировки обязательна"
            );
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_ADJUSTMENT_REASON_TOO_LONG",
                    "Причина не должна превышать 500 символов"
            );
        }
        return normalized;
    }

    private static final class SummaryAccumulator {
        private final Long absencePeriodId;
        private final String vacationFrom;
        private final String vacationTo;
        private final Map<String, CurrencyAccumulator> currencies = new LinkedHashMap<>();

        private SummaryAccumulator(Long absencePeriodId, String vacationFrom, String vacationTo) {
            this.absencePeriodId = absencePeriodId;
            this.vacationFrom = vacationFrom;
            this.vacationTo = vacationTo;
        }

        Long absencePeriodId() { return absencePeriodId; }
    }

    private static final class CurrencyAccumulator {
        private BigInteger receivedBeforeRefunds = BigInteger.ZERO;
        private BigInteger refunded = BigInteger.ZERO;
        private BigInteger netRetained = BigInteger.ZERO;
    }
}
