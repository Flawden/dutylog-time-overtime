package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayUpdateRequest;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Production-calendar truth sits between base schedule norm and future money rules.
 * It never creates absences and never mutates DayEntry shifts.
 */
@Service
public class ProductionCalendarService {
    private static final String BASE = "BASE";
    private static final String LOCAL = "LOCAL_OVERRIDE";
    private static final List<String> KINDS = List.of(
            "NORMAL", "HOLIDAY", "TRANSFERRED_DAY_OFF", "TRANSFERRED_WORKDAY", "SHORTENED_DAY");
    private static final List<String> SCHEDULE_EFFECTS = List.of("NONE", "NORM_OVERRIDE");
    private static final List<String> PAYROLL_EFFECTS = List.of("NONE", "HOLIDAY");

    private final ProductionCalendarDayRepository days;
    private final DayEntryRepository scheduleDays;
    private final WorkNormService workNorm;
    private final AccountingPeriodLockService periodLocks;

    public ProductionCalendarService(ProductionCalendarDayRepository days,
                                     DayEntryRepository scheduleDays,
                                     WorkNormService workNorm,
                                     AccountingPeriodLockService periodLocks) {
        this.days = days;
        this.scheduleDays = scheduleDays;
        this.workNorm = workNorm;
        this.periodLocks = periodLocks;
    }

    @Transactional(readOnly = true)
    public ProductionCalendarMonthDto month(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        Map<LocalDate, DayEntry> schedule = new LinkedHashMap<>();
        for (DayEntry entry : scheduleDays.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            schedule.put(entry.getDate(), entry);
        }

        Map<LocalDate, ProductionCalendarDay> base = new LinkedHashMap<>();
        Map<LocalDate, ProductionCalendarDay> local = new LinkedHashMap<>();
        for (ProductionCalendarDay item : days.findByOwnerAndDateBetweenOrderByDateAscLayerAsc(user, from, to)) {
            if (LOCAL.equals(item.getLayer())) local.put(item.getDate(), item);
            else if (BASE.equals(item.getLayer())) base.put(item.getDate(), item);
        }

        List<ProductionCalendarDayDto> rows = new ArrayList<>();
        int baseNorm = 0;
        int productionNorm = 0;
        int holidayReduction = 0;
        int shortenedReduction = 0;
        int transferredAdjustment = 0;
        int affectedDays = 0;
        int scheduleCoverageDays = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayEntry scheduleEntry = schedule.get(date);
            int baseMinutes = workNorm.basePlannedMinutes(scheduleEntry);
            if (scheduleEntry != null && scheduleEntry.getShiftType() != null) scheduleCoverageDays++;

            ProductionCalendarDay effective = local.getOrDefault(date, base.get(date));
            int productionMinutes = baseMinutes;
            if (effective != null && "NORM_OVERRIDE".equals(effective.getScheduleEffect())) {
                productionMinutes = effective.getNormMinutesOverride() == null ? baseMinutes : effective.getNormMinutesOverride();
            }
            int adjustment = productionMinutes - baseMinutes;
            baseNorm += baseMinutes;
            productionNorm += productionMinutes;

            if (effective != null) {
                affectedDays++;
                switch (effective.getDayKind()) {
                    case "HOLIDAY" -> { if (adjustment < 0) holidayReduction += -adjustment; }
                    case "SHORTENED_DAY" -> { if (adjustment < 0) shortenedReduction += -adjustment; }
                    case "TRANSFERRED_DAY_OFF", "TRANSFERRED_WORKDAY" -> transferredAdjustment += adjustment;
                    default -> { }
                }
            }

            rows.add(toDto(date, effective, local.containsKey(date), baseMinutes, productionMinutes));
        }

        return new ProductionCalendarMonthDto(
                month.toString(), baseNorm, productionNorm, productionNorm - baseNorm,
                holidayReduction, shortenedReduction, transferredAdjustment,
                affectedDays, scheduleCoverageDays, scheduleCoverageDays == month.lengthOfMonth(),
                List.copyOf(rows));
    }

    @Transactional
    public ProductionCalendarDayDto upsertLocal(AppUser user, String dateText, ProductionCalendarDayUpdateRequest request) {
        LocalDate date = parseDate(dateText);
        periodLocks.assertOpen(user, date);
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");

        String kind = normalize(request.dayKind(), KINDS, "Некорректный тип производственного дня");
        String scheduleEffect = normalize(request.scheduleEffect(), SCHEDULE_EFFECTS, "Некорректное влияние на норму");
        String payrollEffect = normalize(request.payrollEffect(), PAYROLL_EFFECTS, "Некорректная категория оплаты");
        Integer normOverride = request.normMinutesOverride();
        if ("NORM_OVERRIDE".equals(scheduleEffect)) {
            if (normOverride == null || normOverride < 0 || normOverride > 1440) {
                throw ApiException.badRequest("PRODUCTION_NORM_INVALID", "Для переопределения нормы укажи 0–1440 минут");
            }
        } else {
            normOverride = null;
        }

        String label = cleanOptional(request.label(), 120);
        ProductionCalendarDay item = days.findByOwnerAndDateAndLayer(user, date, LOCAL)
                .orElseGet(() -> new ProductionCalendarDay(user, date, LOCAL));
        item.update(kind, scheduleEffect, normOverride, payrollEffect, label, "CUSTOM", null);
        days.saveAndFlush(item);
        return resolvedDay(user, date);
    }

    @Transactional
    public void deleteLocal(AppUser user, String dateText) {
        LocalDate date = parseDate(dateText);
        periodLocks.assertOpen(user, date);
        days.findByOwnerAndDateAndLayer(user, date, LOCAL).ifPresent(days::delete);
        days.flush();
    }

    @Transactional(readOnly = true)
    public int requiredMinutes(AppUser user, LocalDate date, DayEntry schedule) {
        int baseMinutes = workNorm.basePlannedMinutes(schedule);
        ProductionCalendarDay local = days.findByOwnerAndDateAndLayer(user, date, LOCAL).orElse(null);
        ProductionCalendarDay base = days.findByOwnerAndDateAndLayer(user, date, BASE).orElse(null);
        ProductionCalendarDay effective = local != null ? local : base;
        return effective != null && "NORM_OVERRIDE".equals(effective.getScheduleEffect())
                && effective.getNormMinutesOverride() != null ? effective.getNormMinutesOverride() : baseMinutes;
    }

    @Transactional(readOnly = true)
    public ProductionCalendarDayDto resolvedDay(AppUser user, LocalDate date) {
        DayEntry schedule = scheduleDays.findByOwnerAndDate(user, date).orElse(null);
        int baseMinutes = workNorm.basePlannedMinutes(schedule);
        ProductionCalendarDay local = days.findByOwnerAndDateAndLayer(user, date, LOCAL).orElse(null);
        ProductionCalendarDay base = days.findByOwnerAndDateAndLayer(user, date, BASE).orElse(null);
        ProductionCalendarDay effective = local != null ? local : base;
        int productionMinutes = requiredMinutes(user, date, schedule);
        return toDto(date, effective, local != null, baseMinutes, productionMinutes);
    }

    private ProductionCalendarDayDto toDto(LocalDate date, ProductionCalendarDay effective, boolean localOverride,
                                            int baseMinutes, int productionMinutes) {
        if (effective == null) {
            return new ProductionCalendarDayDto(date.toString(), "NORMAL", "NONE", null, "NONE", null,
                    "NONE", null, false, baseMinutes, productionMinutes, productionMinutes - baseMinutes);
        }
        return new ProductionCalendarDayDto(date.toString(), effective.getDayKind(), effective.getScheduleEffect(),
                effective.getNormMinutesOverride(), effective.getPayrollEffect(), effective.getLabel(),
                effective.getSourceType(), effective.getSourceRef(), localOverride,
                baseMinutes, productionMinutes, productionMinutes - baseMinutes);
    }

    private YearMonth parseMonth(String text) {
        try { return YearMonth.parse(text); }
        catch (DateTimeParseException ex) { throw ApiException.badRequest("Месяц должен быть в формате yyyy-MM"); }
    }

    private LocalDate parseDate(String text) {
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException ex) { throw ApiException.badRequest("Дата должна быть в формате yyyy-MM-dd"); }
    }

    private String normalize(String raw, List<String> allowed, String message) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw ApiException.badRequest(message);
        return value;
    }

    private String cleanOptional(String raw, int max) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isBlank()) return null;
        if (value.length() > max) throw ApiException.badRequest("Текст слишком длинный");
        return value;
    }
}
