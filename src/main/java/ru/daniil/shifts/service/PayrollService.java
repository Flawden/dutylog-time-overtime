package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;
import ru.daniil.shifts.service.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * First money layer. It never reinterprets calendar entities directly: all time values
 * come from TimeCompensationService's posted-only payroll source. Final calculation is
 * allowed only for a closed month with a healthy ledger and produces a new immutable revision.
 */
@Service
public class PayrollService {
    private final PayrollSettingsRepository settings;
    private final PayrollAdjustmentRepository adjustments;
    private final PayrollSnapshotRepository snapshots;
    private final TimeAccountingPeriodRepository accountingPeriods;
    private final TimeCompensationService timeCompensation;
    private final LedgerIntegrityService ledgerIntegrity;

    public PayrollService(PayrollSettingsRepository settings,
                          PayrollAdjustmentRepository adjustments,
                          PayrollSnapshotRepository snapshots,
                          TimeAccountingPeriodRepository accountingPeriods,
                          TimeCompensationService timeCompensation,
                          LedgerIntegrityService ledgerIntegrity) {
        this.settings = settings;
        this.adjustments = adjustments;
        this.snapshots = snapshots;
        this.accountingPeriods = accountingPeriods;
        this.timeCompensation = timeCompensation;
        this.ledgerIntegrity = ledgerIntegrity;
    }

    @Transactional
    public PayrollPeriodDto period(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        PayrollSettings payrollSettings = ensureSettings(user);
        return buildPeriod(user, month, payrollSettings);
    }

    @Transactional
    public PayrollSettingsDto updateSettings(AppUser user, PayrollSettingsUpdateRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        String currency = normalizeCurrency(request.currencyCode());
        long rate = request.hourlyRateMinor() == null ? -1L : request.hourlyRateMinor();
        if (rate < 0 || rate > 1_000_000_000L) {
            throw ApiException.badRequest("PAYROLL_RATE_INVALID", "Некорректная почасовая ставка");
        }
        PayrollSettings value = ensureSettings(user);
        value.update(currency, rate);
        return toSettings(settings.saveAndFlush(value));
    }

    @Transactional
    public PayrollAdjustmentDto addAdjustment(AppUser user, PayrollAdjustmentRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        YearMonth month = parseMonth(request.month());
        requireClosedPeriod(user, month, true);
        String type = normalizeAdjustmentType(request.adjustmentType());
        long amount = request.amountMinor() == null ? 0L : request.amountMinor();
        if (amount < 1L || amount > 1_000_000_000_000L) {
            throw ApiException.badRequest("PAYROLL_ADJUSTMENT_INVALID", "Некорректная сумма корректировки");
        }
        String title = cleanRequired(request.title(), 120, "Название корректировки обязательно");
        String note = cleanOptional(request.note(), 500);
        PayrollAdjustment saved = adjustments.saveAndFlush(new PayrollAdjustment(
                user, month.atDay(1), type, amount, title, note));
        return toAdjustment(saved);
    }

    @Transactional
    public PayrollSnapshotDto calculate(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        TimeAccountingPeriod period = requireClosedPeriod(user, month, true);
        LedgerIntegrityDto integrity = ledgerIntegrity.inspect(user, month.atDay(1), month.atEndOfMonth());
        if (!integrity.healthy()) {
            throw ApiException.conflict("LEDGER_INTEGRITY_FAILED",
                    "Расчёт зарплаты заблокирован: сначала исправь расхождения журнала");
        }
        PayrollSettings payrollSettings = ensureSettings(user);
        if (payrollSettings.getHourlyRateMinor() <= 0) {
            throw ApiException.conflict("PAYROLL_RATE_REQUIRED", "Сначала укажи почасовую ставку");
        }

        PayrollSourceSnapshot source = timeCompensation.payrollSource(user, month.atDay(1), month.atEndOfMonth());
        List<PayrollAdjustment> monthAdjustments = adjustments
                .findByOwnerAndPeriodMonthOrderByIdAsc(user, month.atDay(1));
        PayrollPreviewDto preview = preview(month, payrollSettings, source, monthAdjustments);
        PayrollSnapshot previous = snapshots
                .findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(user, month.atDay(1)).orElse(null);
        int revision = previous == null ? 1 : previous.getRevision() + 1;
        Instant checkedAt = Instant.now();
        String calculationHash = calculationHash(preview, period.getClosedAt(), monthAdjustments);

        PayrollSnapshot created = snapshots.saveAndFlush(new PayrollSnapshot(
                user, month.atDay(1), revision, preview.currencyCode(), preview.hourlyRateMinor(),
                preview.plannedMinutes(), preview.workedMinutes(), preview.vacationMinutes(),
                preview.sickMinutes(), preview.overtimeCompensatedMinutes(), preview.unpaidMinutes(),
                preview.timeAdjustmentMinutes(), preview.paidAbsenceMinutes(), preview.payableMinutes(), preview.basePayMinor(),
                preview.additionsMinor(), preview.deductionsMinor(), preview.totalPayMinor(),
                period.getClosedAt(), checkedAt, calculationHash));
        if (previous != null) {
            previous.supersedeWith(created);
            snapshots.save(previous);
        }
        return toSnapshot(created);
    }

    private PayrollPeriodDto buildPeriod(AppUser user, YearMonth month, PayrollSettings payrollSettings) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        TimeAccountingPeriod accountingPeriod = accountingPeriods.findByOwnerAndPeriodMonth(user, first).orElse(null);
        boolean closed = accountingPeriod != null && accountingPeriod.isClosed();
        LedgerIntegrityDto integrity = ledgerIntegrity.inspect(user, first, last);
        PayrollSourceSnapshot source = timeCompensation.payrollSource(user, first, last);
        List<PayrollAdjustment> monthAdjustments = adjustments.findByOwnerAndPeriodMonthOrderByIdAsc(user, first);
        PayrollPreviewDto preview = preview(month, payrollSettings, source, monthAdjustments);
        List<PayrollSnapshotDto> history = snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(user, first)
                .stream().map(this::toSnapshot).toList();
        boolean rateReady = payrollSettings.getHourlyRateMinor() > 0;
        boolean canCalculate = closed && integrity.healthy() && rateReady;
        String blockingReason = !closed ? "PERIOD_OPEN"
                : !integrity.healthy() ? "LEDGER_INTEGRITY_FAILED"
                : !rateReady ? "PAYROLL_RATE_REQUIRED" : null;
        return new PayrollPeriodDto(month.toString(), closed, integrity.healthy(), canCalculate, blockingReason,
                toSettings(payrollSettings), preview,
                monthAdjustments.stream().map(this::toAdjustment).toList(),
                history.isEmpty() ? null : history.get(0), history);
    }

    private PayrollPreviewDto preview(YearMonth month, PayrollSettings settings,
                                      PayrollSourceSnapshot source, List<PayrollAdjustment> monthAdjustments) {
        long additions = monthAdjustments.stream()
                .filter(item -> "ADDITION".equals(item.getAdjustmentType()))
                .mapToLong(PayrollAdjustment::getAmountMinor).sum();
        long deductions = monthAdjustments.stream()
                .filter(item -> "DEDUCTION".equals(item.getAdjustmentType()))
                .mapToLong(PayrollAdjustment::getAmountMinor).sum();
        long basePay = moneyForMinutes(source.payableMinutes(), settings.getHourlyRateMinor());
        long totalPay = safeMoney(basePay, additions, deductions);
        return new PayrollPreviewDto(month.toString(), settings.getCurrencyCode(), settings.getHourlyRateMinor(),
                source.plannedMinutes(), source.workedMinutes(), source.vacationMinutes(), source.sickMinutes(),
                source.overtimeCompensatedMinutes(), source.unpaidMinutes(), source.timeAdjustmentMinutes(),
                source.paidAbsenceMinutes(), source.payableMinutes(), basePay, additions, deductions, totalPay);
    }

    private TimeAccountingPeriod requireClosedPeriod(AppUser user, YearMonth month, boolean lock) {
        TimeAccountingPeriod period = (lock
                ? accountingPeriods.findForUpdateByOwnerAndPeriodMonth(user, month.atDay(1))
                : accountingPeriods.findByOwnerAndPeriodMonth(user, month.atDay(1)))
                .orElseThrow(() -> ApiException.conflict("PERIOD_NOT_CLOSED",
                        "Сначала закрой расчётный период " + month));
        if (!period.isClosed()) {
            throw ApiException.conflict("PERIOD_NOT_CLOSED", "Сначала закрой расчётный период " + month);
        }
        return period;
    }

    private PayrollSettings ensureSettings(AppUser user) {
        return settings.findByOwner(user).orElseGet(() -> settings.saveAndFlush(new PayrollSettings(user)));
    }

    private long moneyForMinutes(int minutes, long hourlyRateMinor) {
        try {
            return BigDecimal.valueOf(hourlyRateMinor)
                    .multiply(BigDecimal.valueOf(Math.max(0, minutes)))
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("PAYROLL_AMOUNT_OVERFLOW", "Сумма расчёта слишком велика");
        }
    }

    private long safeMoney(long basePay, long additions, long deductions) {
        try {
            return BigDecimal.valueOf(basePay).add(BigDecimal.valueOf(additions))
                    .subtract(BigDecimal.valueOf(deductions)).longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("PAYROLL_AMOUNT_OVERFLOW", "Итоговая сумма слишком велика");
        }
    }

    private String calculationHash(PayrollPreviewDto preview, Instant closedAt,
                                   List<PayrollAdjustment> monthAdjustments) {
        StringBuilder canonical = new StringBuilder()
                .append(preview.month()).append('|').append(preview.currencyCode()).append('|')
                .append(preview.hourlyRateMinor()).append('|').append(preview.plannedMinutes()).append('|')
                .append(preview.workedMinutes()).append('|').append(preview.vacationMinutes()).append('|')
                .append(preview.sickMinutes()).append('|').append(preview.overtimeCompensatedMinutes()).append('|')
                .append(preview.unpaidMinutes()).append('|').append(preview.timeAdjustmentMinutes()).append('|')
                .append(preview.paidAbsenceMinutes()).append('|').append(preview.payableMinutes()).append('|')
                .append(preview.basePayMinor()).append('|')
                .append(preview.additionsMinor()).append('|').append(preview.deductionsMinor()).append('|')
                .append(preview.totalPayMinor()).append('|').append(closedAt == null ? "" : closedAt.toString());
        for (PayrollAdjustment item : monthAdjustments) {
            canonical.append('|').append(item.getId()).append(':').append(item.getAdjustmentType())
                    .append(':').append(item.getAmountMinor()).append(':').append(item.getTitle());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private PayrollSettingsDto toSettings(PayrollSettings value) {
        return new PayrollSettingsDto(value.getCurrencyCode(), value.getHourlyRateMinor(),
                value.getUpdatedAt() == null ? null : value.getUpdatedAt().toString());
    }

    private PayrollAdjustmentDto toAdjustment(PayrollAdjustment value) {
        return new PayrollAdjustmentDto(value.getId(), YearMonth.from(value.getPeriodMonth()).toString(),
                value.getAdjustmentType(), value.getAmountMinor(), value.getTitle(), value.getNote(),
                value.getCreatedAt() == null ? null : value.getCreatedAt().toString());
    }

    private PayrollSnapshotDto toSnapshot(PayrollSnapshot value) {
        return new PayrollSnapshotDto(value.getId(), YearMonth.from(value.getPeriodMonth()).toString(),
                value.getRevision(), value.getCurrencyCode(), value.getHourlyRateMinor(), value.getPlannedMinutes(),
                value.getWorkedMinutes(), value.getVacationMinutes(), value.getSickMinutes(),
                value.getOvertimeCompensatedMinutes(), value.getUnpaidMinutes(), value.getTimeAdjustmentMinutes(),
                value.getPaidAbsenceMinutes(), value.getPayableMinutes(), value.getBasePayMinor(), value.getAdditionsMinor(),
                value.getDeductionsMinor(), value.getTotalPayMinor(), value.getSourcePeriodClosedAt().toString(),
                value.getSourceIntegrityCheckedAt().toString(), value.getCalculationHash(),
                value.getCreatedAt() == null ? null : value.getCreatedAt().toString(),
                value.getSupersededBy() == null ? null : value.getSupersededBy().getId());
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("PAYROLL_MONTH_INVALID", "Месяц должен быть в формате yyyy-MM");
        }
    }

    private String normalizeCurrency(String value) {
        String currency = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw ApiException.badRequest("PAYROLL_CURRENCY_INVALID", "Код валюты должен состоять из трёх букв");
        }
        return currency;
    }

    private String normalizeAdjustmentType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"ADDITION".equals(type) && !"DEDUCTION".equals(type)) {
            throw ApiException.badRequest("PAYROLL_ADJUSTMENT_TYPE_INVALID", "Тип должен быть ADDITION или DEDUCTION");
        }
        return type;
    }

    private String cleanRequired(String value, int max, String message) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw ApiException.badRequest(message);
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private String cleanOptional(String value, int max) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return null;
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
