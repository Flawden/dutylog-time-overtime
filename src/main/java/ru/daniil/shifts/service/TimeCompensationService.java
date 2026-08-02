package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.AbsenceOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditRowDto;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageDto;
import ru.daniil.shifts.dto.Dtos.TimeCompensationDayDto;
import ru.daniil.shifts.dto.Dtos.LedgerIntegrityDto;
import ru.daniil.shifts.dto.Dtos.TimeCompensationSummaryDto;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.model.TimeLedgerEntry;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.TimeLedgerEntryRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read model that joins schedule, factual absences and compensation movements.
 * It intentionally stops before money rules: v27.28 Payroll Foundation consumes
 * this projection instead of re-interpreting calendar data independently.
 */
@Service
public class TimeCompensationService {
    private final DayEntryRepository days;
    private final ActualWorkIntervalRepository actualWork;
    private final VacationPlannerService vacationPlanner;
    private final OvertimeService overtime;
    private final LedgerIntegrityService ledgerIntegrity;
    private final TimeLedgerEntryRepository ledgerEntries;

    public TimeCompensationService(DayEntryRepository days,
                                   ActualWorkIntervalRepository actualWork,
                                   VacationPlannerService vacationPlanner,
                                   OvertimeService overtime,
                                   LedgerIntegrityService ledgerIntegrity,
                                   TimeLedgerEntryRepository ledgerEntries) {
        this.days = days;
        this.actualWork = actualWork;
        this.vacationPlanner = vacationPlanner;
        this.overtime = overtime;
        this.ledgerIntegrity = ledgerIntegrity;
        this.ledgerEntries = ledgerEntries;
    }

    @Transactional
    public TimeCompensationSummaryDto summary(AppUser user, LocalDate from, LocalDate to) {
        Map<LocalDate, DayEntry> planned = new LinkedHashMap<>();
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            planned.put(entry.getDate(), entry);
        }

        Map<LocalDate, List<ActualWorkInterval>> actualByDate = new LinkedHashMap<>();
        for (ActualWorkInterval interval : actualWork
                .findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(user, from, to)) {
            actualByDate.computeIfAbsent(interval.getWorkDate(), ignored -> new ArrayList<>()).add(interval);
        }

        Map<LocalDate, List<AbsenceOccurrenceDto>> absences = new LinkedHashMap<>();
        for (AbsenceOccurrenceDto occurrence : vacationPlanner.occurrences(user, from, to)) {
            absences.computeIfAbsent(LocalDate.parse(occurrence.date()), ignored -> new ArrayList<>()).add(occurrence);
        }

        OvertimeAccountDto account = overtime.account(user);
        Map<LocalDate, Integer> earnedByDate = new LinkedHashMap<>();
        for (OvertimeCreditRowDto credit : account.credits()) {
            LocalDate date = LocalDate.parse(credit.workedDate());
            if (!date.isBefore(from) && !date.isAfter(to)) {
                earnedByDate.merge(date, credit.creditedMinutes(), Integer::sum);
            }
        }
        Map<LocalDate, Integer> usedByDate = new LinkedHashMap<>();
        Map<LocalDate, Integer> compensatedByDate = new LinkedHashMap<>();
        for (OvertimeUsageDto usage : account.usages()) {
            LocalDate date = LocalDate.parse(usage.usageDate());
            if (!date.isBefore(from) && !date.isAfter(to)) {
                usedByDate.merge(date, usage.minutes(), Integer::sum);
                if ("ABSENCE".equals(usage.sourceKind())) {
                    compensatedByDate.merge(date, usage.minutes(), Integer::sum);
                }
            }
        }

        List<TimeCompensationDayDto> rows = new ArrayList<>();
        int plannedTotal = 0;
        int workedTotal = 0;
        int absenceTotal = 0;
        int earnedTotal = 0;
        int usedTotal = 0;
        int compensatedTotal = 0;
        int vacationDays = 0;
        int sickTotal = 0;
        int unpaidTotal = 0;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayEntry day = planned.get(date);
            int plannedMinutes = plannedMinutes(day);
            List<AbsenceOccurrenceDto> dayAbsences = absences.getOrDefault(date, List.of());
            int absenceMinutes = absenceMinutes(plannedMinutes, dayAbsences);
            int earnedMinutes = earnedByDate.getOrDefault(date, 0);
            List<ActualWorkInterval> actualIntervals = actualByDate.getOrDefault(date, List.of());
            boolean explicitActual = !actualIntervals.isEmpty();
            int workedMinutes = explicitActual
                    ? actualIntervals.stream().mapToInt(ActualWorkInterval::getWorkedMinutes).sum()
                    : Math.max(0, plannedMinutes - Math.min(plannedMinutes, absenceMinutes)) + earnedMinutes;
            int usedMinutes = usedByDate.getOrDefault(date, 0);
            int compensatedMinutes = compensatedByDate.getOrDefault(date, 0);
            int dayVacationDays = (int) dayAbsences.stream().filter(AbsenceOccurrenceDto::countedDay).count();
            int sickMinutes = policyMinutes(plannedMinutes, dayAbsences, "SICK_PAY");
            int unpaidMinutes = policyMinutes(plannedMinutes, dayAbsences, "UNPAID");

            if (plannedMinutes > 0 || absenceMinutes > 0 || earnedMinutes > 0 || usedMinutes > 0 || explicitActual) {
                rows.add(new TimeCompensationDayDto(
                        date.toString(),
                        plannedMinutes,
                        workedMinutes,
                        absenceMinutes,
                        earnedMinutes,
                        usedMinutes,
                        compensatedMinutes,
                        dayVacationDays,
                        sickMinutes,
                        unpaidMinutes,
                        explicitActual ? "Фактически отмечено · " + minutesLabel(workedMinutes)
                                : factLabel(dayAbsences, workedMinutes, plannedMinutes),
                        compensationLabel(dayAbsences),
                        dayAbsences.stream().map(AbsenceOccurrenceDto::periodId).distinct().toList(),
                        explicitActual ? "EXPLICIT" : "PLAN_DERIVED",
                        actualIntervals.stream().map(ActualWorkInterval::getId).toList()
                ));
            }

            plannedTotal += plannedMinutes;
            workedTotal += workedMinutes;
            absenceTotal += absenceMinutes;
            earnedTotal += earnedMinutes;
            usedTotal += usedMinutes;
            compensatedTotal += compensatedMinutes;
            vacationDays += dayVacationDays;
            sickTotal += sickMinutes;
            unpaidTotal += unpaidMinutes;
        }

        int reservedMinutes = account.usages().stream().filter(item -> "RESERVED".equals(item.postingState()))
                .mapToInt(OvertimeUsageDto::minutes).sum();
        int postedMinutes = account.usages().stream().filter(item -> "POSTED".equals(item.postingState()))
                .mapToInt(OvertimeUsageDto::minutes).sum();
        LedgerIntegrityDto integrity = ledgerIntegrity.inspect(user, from, to);
        long monthCount = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
        boolean periodClosed = integrity.periods().size() == monthCount
                && integrity.periods().stream().allMatch(item -> "CLOSED".equals(item.status()));

        return new TimeCompensationSummaryDto(
                from.toString(), to.toString(), plannedTotal, workedTotal, absenceTotal,
                earnedTotal, usedTotal, (int) Math.round(account.balanceHours() * 60.0),
                compensatedTotal, vacationDays, sickTotal, unpaidTotal,
                reservedMinutes, postedMinutes, integrity.healthy(), periodClosed, List.copyOf(rows)
        );
    }

    /**
     * Canonical posted-only source for money calculation. Planned/submitted absences stay
     * visible in the operational UI but never enter payroll until APPROVED or COMPLETED.
     * PayrollService consumes this projection instead of joining calendar tables itself.
     */
    @Transactional
    public PayrollSourceSnapshot payrollSource(AppUser user, LocalDate from, LocalDate to) {
        Map<LocalDate, DayEntry> planned = new LinkedHashMap<>();
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            planned.put(entry.getDate(), entry);
        }

        Map<LocalDate, List<ActualWorkInterval>> actualByDate = new LinkedHashMap<>();
        for (ActualWorkInterval interval : actualWork
                .findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(user, from, to)) {
            actualByDate.computeIfAbsent(interval.getWorkDate(), ignored -> new ArrayList<>()).add(interval);
        }

        Map<LocalDate, List<AbsenceOccurrenceDto>> postedAbsences = new LinkedHashMap<>();
        for (AbsenceOccurrenceDto occurrence : vacationPlanner.occurrences(user, from, to)) {
            if (!isPostedStatus(occurrence.status())) continue;
            postedAbsences.computeIfAbsent(LocalDate.parse(occurrence.date()), ignored -> new ArrayList<>()).add(occurrence);
        }

        OvertimeAccountDto account = overtime.account(user);
        Map<LocalDate, Integer> earnedByDate = new LinkedHashMap<>();
        for (OvertimeCreditRowDto credit : account.credits()) {
            LocalDate date = LocalDate.parse(credit.workedDate());
            if (!date.isBefore(from) && !date.isAfter(to)) {
                earnedByDate.merge(date, credit.creditedMinutes(), Integer::sum);
            }
        }

        int plannedTotal = 0;
        int workedTotal = 0;
        int vacationTotal = 0;
        int sickTotal = 0;
        int overtimeCompensatedTotal = 0;
        int unpaidTotal = 0;
        int otherUnpaidTotal = 0;
        List<PayrollSourceDay> sourceDays = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int plannedMinutes = plannedMinutes(planned.get(date));
            List<AbsenceOccurrenceDto> absences = postedAbsences.getOrDefault(date, List.of());
            int absenceMinutes = absenceMinutes(plannedMinutes, absences);
            int earnedMinutes = earnedByDate.getOrDefault(date, 0);
            List<ActualWorkInterval> actualIntervals = actualByDate.getOrDefault(date, List.of());
            int workedMinutes = actualIntervals.isEmpty()
                    ? Math.max(0, plannedMinutes - Math.min(plannedMinutes, absenceMinutes)) + earnedMinutes
                    : actualIntervals.stream().mapToInt(ActualWorkInterval::getWorkedMinutes).sum();
            int vacationMinutes = policyMinutes(plannedMinutes, absences, "VACATION_ALLOWANCE");
            int sickMinutes = policyMinutes(plannedMinutes, absences, "SICK_PAY");
            int overtimeMinutes = policyMinutes(plannedMinutes, absences, "OVERTIME_BANK");
            int unpaidMinutes = policyMinutes(plannedMinutes, absences, "UNPAID");
            int classified = vacationMinutes + sickMinutes + overtimeMinutes + unpaidMinutes;
            int otherUnpaidMinutes = Math.max(0, absenceMinutes - classified);

            if (plannedMinutes > 0 || workedMinutes > 0 || absenceMinutes > 0) {
                sourceDays.add(new PayrollSourceDay(date, plannedMinutes, workedMinutes, vacationMinutes,
                        sickMinutes, overtimeMinutes, unpaidMinutes + otherUnpaidMinutes));
            }
            plannedTotal += plannedMinutes;
            workedTotal += workedMinutes;
            vacationTotal += vacationMinutes;
            sickTotal += sickMinutes;
            overtimeCompensatedTotal += overtimeMinutes;
            unpaidTotal += unpaidMinutes;
            otherUnpaidTotal += otherUnpaidMinutes;
        }

        int timeAdjustmentMinutes = ledgerEntries
                .findByOwnerAndEffectiveDateBetweenOrderByEffectiveDateAscIdAsc(user, from, to).stream()
                .filter(item -> "MANUAL_ADJUSTMENT".equals(item.getEntryKind()))
                .mapToInt(TimeLedgerEntry::getSignedMinutes)
                .sum();
        int paidAbsenceMinutes = vacationTotal + sickTotal + overtimeCompensatedTotal;
        int payableMinutes = Math.max(0, workedTotal + paidAbsenceMinutes + timeAdjustmentMinutes);
        return new PayrollSourceSnapshot(from, to, plannedTotal, workedTotal, vacationTotal, sickTotal,
                overtimeCompensatedTotal, unpaidTotal + otherUnpaidTotal, timeAdjustmentMinutes,
                paidAbsenceMinutes, payableMinutes, List.copyOf(sourceDays));
    }

    private boolean isPostedStatus(String status) {
        return "APPROVED".equals(status) || "COMPLETED".equals(status);
    }

    public record PayrollSourceDay(LocalDate date, int plannedMinutes, int workedMinutes,
                                   int vacationMinutes, int sickMinutes,
                                   int overtimeCompensatedMinutes, int unpaidMinutes) {}

    public record PayrollSourceSnapshot(LocalDate from, LocalDate to, int plannedMinutes, int workedMinutes,
                                        int vacationMinutes, int sickMinutes, int overtimeCompensatedMinutes,
                                        int unpaidMinutes, int timeAdjustmentMinutes, int paidAbsenceMinutes,
                                        int payableMinutes, List<PayrollSourceDay> days) {}

    private int plannedMinutes(DayEntry entry) {
        if (entry == null || entry.getShiftType() == null) return 0;
        if (entry.getShiftNetMinutes() > 0) return Math.toIntExact(entry.getShiftNetMinutes());
        ShiftType shift = entry.getShiftType();
        return Math.max(0, (int) Math.round(shift.effectivePlannedHours() * 60.0));
    }

    private int absenceMinutes(int plannedMinutes, List<AbsenceOccurrenceDto> absences) {
        if (absences.stream().anyMatch(item -> "FULL_DAY".equals(item.coverage()) && item.replacesShift())) {
            return plannedMinutes;
        }
        int partial = absences.stream()
                .filter(item -> "PARTIAL".equals(item.coverage()))
                .mapToInt(this::partialMinutes)
                .sum();
        return plannedMinutes > 0 ? Math.min(plannedMinutes, partial) : partial;
    }

    private int policyMinutes(int plannedMinutes, List<AbsenceOccurrenceDto> absences, String policy) {
        int total = 0;
        for (AbsenceOccurrenceDto item : absences) {
            if (!policy.equals(item.compensationPolicy())) continue;
            total += "PARTIAL".equals(item.coverage()) ? partialMinutes(item) : plannedMinutes;
        }
        return total;
    }

    private int partialMinutes(AbsenceOccurrenceDto item) {
        if (item.startTime() == null || item.endTime() == null) return 0;
        return Math.toIntExact(Duration.between(LocalTime.parse(item.startTime()), LocalTime.parse(item.endTime())).toMinutes());
    }

    private String minutesLabel(int minutes) {
        int safe = Math.max(0, minutes);
        return (safe / 60) + " ч " + (safe % 60) + " мин";
    }

    private String factLabel(List<AbsenceOccurrenceDto> absences, int workedMinutes, int plannedMinutes) {
        if (absences.isEmpty()) return plannedMinutes > 0 ? "Отработано по графику" : "Выходной по графику";
        AbsenceOccurrenceDto first = absences.get(0);
        String title = first.title() == null || first.title().isBlank() ? first.typeName() : first.title();
        if (workedMinutes > 0) return title + " · часть смены";
        return title;
    }

    private String compensationLabel(List<AbsenceOccurrenceDto> absences) {
        if (absences.isEmpty()) return "Без компенсационных операций";
        String policy = absences.get(0).compensationPolicy();
        return switch (policy == null ? "NONE" : policy) {
            case "OVERTIME_BANK" -> "Списано из банка переработок";
            case "VACATION_ALLOWANCE" -> "Списано из отпускного баланса";
            case "SICK_PAY" -> "Больничная политика оплаты";
            case "UNPAID" -> "Неоплачиваемое время";
            default -> "Без отдельного источника покрытия";
        };
    }
}
