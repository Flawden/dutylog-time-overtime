package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Approval lifecycle, append-only audit and cross-ledger reconciliation.
 * The mutable operational FIFO tables remain fast, while every meaningful
 * absence transition is explained by an immutable time_ledger_entries row.
 */
@Service
public class LedgerIntegrityService {
    public static final Set<String> WORKFLOW_STATUSES = Set.of(
            "DRAFT", "PLANNED", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED");
    private static final Set<String> RESERVED_STATUSES = Set.of("PLANNED", "SUBMITTED");
    private static final Set<String> POSTED_STATUSES = Set.of("APPROVED", "COMPLETED");

    private final TimeLedgerEntryRepository entries;
    private final TimeAccountingPeriodRepository periods;
    private final AbsencePeriodRepository absences;
    private final OvertimeUsageRepository usages;
    private final OvertimeAllocationRepository allocations;
    private final OvertimeCreditRepository credits;
    private final OvertimeService overtime;

    public LedgerIntegrityService(TimeLedgerEntryRepository entries,
                                  TimeAccountingPeriodRepository periods,
                                  AbsencePeriodRepository absences,
                                  OvertimeUsageRepository usages,
                                  OvertimeAllocationRepository allocations,
                                  OvertimeCreditRepository credits,
                                  OvertimeService overtime) {
        this.entries = entries;
        this.periods = periods;
        this.absences = absences;
        this.usages = usages;
        this.allocations = allocations;
        this.credits = credits;
        this.overtime = overtime;
    }

    public String normalizeStatus(String value) {
        String status = value == null || value.isBlank() ? "PLANNED" : value.trim().toUpperCase(Locale.ROOT);
        if (!WORKFLOW_STATUSES.contains(status)) {
            throw ApiException.badRequest("Некорректный статус отсутствия");
        }
        return status;
    }

    public boolean reserves(String status) { return RESERVED_STATUSES.contains(normalizeStatus(status)); }
    public boolean posts(String status) { return POSTED_STATUSES.contains(normalizeStatus(status)); }
    public boolean consumesBalance(String status) { return reserves(status) || posts(status); }
    public boolean visibleAsFact(String status) { return consumesBalance(status); }

    public String usagePostingState(String status) {
        if (posts(status)) return "POSTED";
        if (reserves(status)) return "RESERVED";
        return null;
    }

    public AbsenceLedgerSnapshot snapshot(AbsencePeriod period) {
        if (period == null) return null;
        return new AbsenceLedgerSnapshot(period.getStatus(), period.getCompensationPolicy(),
                period.getCompensatedMinutes(), period.getStartDate());
    }

    @Transactional
    public void recordAbsenceTransition(AppUser user, AbsencePeriod period,
                                        AbsenceLedgerSnapshot previous, String action) {
        if (period == null || period.getId() == null) return;
        AbsenceLedgerSnapshot current = snapshot(period);
        if (Objects.equals(previous, current) && !"DELETE".equals(action)) return;

        TimeLedgerEntry active = entries
                .findFirstByOwnerAndSourceKindAndSourceIdOrderByIdDesc(user, "ABSENCE", period.getId())
                .filter(item -> "RESERVED".equals(item.getPostingState()) || "POSTED".equals(item.getPostingState()))
                .orElse(null);
        if (active != null) {
            entries.save(new TimeLedgerEntry(user, "ABSENCE_REVERSAL", "ABSENCE", period.getId(),
                    period.getStartDate(), -active.getSignedMinutes(), "REVERSED", active,
                    "Отмена предыдущего состояния: " + safe(action)));
        }

        if ("DELETE".equals(action) || !consumesBalance(period.getStatus())) return;
        String state = usagePostingState(period.getStatus());
        int signed = "OVERTIME_BANK".equals(period.getCompensationPolicy())
                ? -Math.max(0, period.getCompensatedMinutes()) : 0;
        String kind = "RESERVED".equals(state) ? "ABSENCE_RESERVATION" : "ABSENCE_POSTING";
        entries.save(new TimeLedgerEntry(user, kind, "ABSENCE", period.getId(), period.getStartDate(),
                signed, state, null, absenceReason(period, action)));
    }

    @Transactional(readOnly = true)
    public void assertRangeOpen(AppUser user, LocalDate from, LocalDate to) {
        if (from == null || to == null) return;
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            LocalDate month = cursor.atDay(1);
            if (periods.findByOwnerAndPeriodMonth(user, month).map(TimeAccountingPeriod::isClosed).orElse(false)) {
                throw ApiException.conflict("PERIOD_CLOSED",
                        "Расчётный период " + cursor + " закрыт. Добавь корректировку или сначала открой период.");
            }
            cursor = cursor.plusMonths(1);
        }
    }

    @Transactional
    public AccountingPeriodDto closePeriod(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        LedgerIntegrityDto integrity = inspect(user, month.atDay(1), month.atEndOfMonth());
        if (!integrity.healthy()) {
            throw ApiException.conflict("LEDGER_INTEGRITY_FAILED",
                    "Нельзя закрыть период: сначала исправь расхождения журнала");
        }
        TimeAccountingPeriod period = periods.findByOwnerAndPeriodMonth(user, month.atDay(1))
                .orElseGet(() -> new TimeAccountingPeriod(user, month.atDay(1)));
        period.close();
        return toDto(periods.saveAndFlush(period));
    }

    @Transactional
    public AccountingPeriodDto reopenPeriod(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        TimeAccountingPeriod period = periods.findByOwnerAndPeriodMonth(user, month.atDay(1))
                .orElseGet(() -> new TimeAccountingPeriod(user, month.atDay(1)));
        period.reopen();
        return toDto(periods.saveAndFlush(period));
    }

    @Transactional
    public TimeLedgerEntryDto addClosedPeriodAdjustment(AppUser user, LedgerAdjustmentRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        YearMonth month = parseMonth(request.month());
        boolean closed = periods.findByOwnerAndPeriodMonth(user, month.atDay(1))
                .map(TimeAccountingPeriod::isClosed).orElse(false);
        if (!closed) {
            throw ApiException.conflict("PERIOD_NOT_CLOSED",
                    "Корректировка задним числом нужна только для закрытого периода");
        }
        int minutes = request.signedMinutes() == null ? 0 : request.signedMinutes();
        if (minutes == 0 || Math.abs(minutes) > 600000) {
            throw ApiException.badRequest("Корректировка должна быть ненулевой и не больше 600000 минут");
        }
        TimeLedgerEntry entry = entries.saveAndFlush(new TimeLedgerEntry(user, "MANUAL_ADJUSTMENT", "PERIOD",
                null, month.atEndOfMonth(), minutes, "POSTED", null, clean(request.reason())));
        return toDto(entry);
    }

    @Transactional
    public LedgerIntegrityDto inspect(AppUser user, LocalDate from, LocalDate to) {
        // Rebuild any missing FIFO allocations before reconciling them.
        overtime.account(user);
        List<LedgerIntegrityIssueDto> issues = new ArrayList<>();
        List<AbsencePeriod> absenceList = absences
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, from, to);
        Map<Long, AbsencePeriod> absenceById = absences.findByOwnerOrderByStartDateAscIdAsc(user).stream()
                .collect(Collectors.toMap(AbsencePeriod::getId, item -> item));
        List<OvertimeUsage> usageList = usages.findByOwnerOrderByUsageDateAscIdAsc(user);
        Map<Long, OvertimeUsage> usageByAbsence = usageList.stream()
                .filter(OvertimeUsage::isAbsenceLinked)
                .collect(Collectors.toMap(OvertimeUsage::getSourceAbsenceId, item -> item, (a, b) -> a));
        Map<Long, Integer> allocationByUsage = allocations.findAllByOwner(user).stream()
                .collect(Collectors.groupingBy(item -> item.getUsage().getId(),
                        Collectors.summingInt(OvertimeAllocation::getAllocatedMinutes)));

        int orphan = 0;
        int allocationMismatch = 0;
        for (OvertimeUsage usage : usageList) {
            if (!usage.isAbsenceLinked()) continue;
            AbsencePeriod absence = absenceById.get(usage.getSourceAbsenceId());
            boolean usageInRange = !usage.getUsageDate().isBefore(from) && !usage.getUsageDate().isAfter(to);
            boolean absenceInRange = absence != null
                    && !absence.getEndDate().isBefore(from) && !absence.getStartDate().isAfter(to);
            if (!usageInRange && !absenceInRange) continue;
            if (absence == null) {
                orphan++;
                issues.add(issue("ORPHAN_LINKED_USAGE", "ERROR",
                        "Связанное списание не имеет отсутствия", "OVERTIME_USAGE", usage.getId()));
                continue;
            }
            String expectedState = usagePostingState(absence.getStatus());
            if (expectedState == null) {
                issues.add(issue("INACTIVE_ABSENCE_HAS_USAGE", "ERROR",
                        "Неактивное отсутствие удерживает часы переработки", "ABSENCE", absence.getId()));
            } else if (!expectedState.equals(usage.getPostingState())) {
                issues.add(issue("USAGE_STATE_MISMATCH", "ERROR",
                        "Статус резерва не совпадает со статусом отсутствия", "ABSENCE", absence.getId()));
            }
            if (absence.getCompensatedMinutes() != usage.getRequestedMinutes()) {
                issues.add(issue("USAGE_MINUTES_MISMATCH", "ERROR",
                        "Минуты отсутствия и связанного списания не совпадают", "ABSENCE", absence.getId()));
            }
            int allocated = allocationByUsage.getOrDefault(usage.getId(), 0);
            if (allocated != usage.getRequestedMinutes()) {
                allocationMismatch++;
                issues.add(issue("ALLOCATION_MISMATCH", "ERROR",
                        "FIFO allocations не сходятся со списанием", "OVERTIME_USAGE", usage.getId()));
            }
        }

        for (AbsencePeriod absence : absenceList) {
            boolean overtimeBacked = "OVERTIME_BANK".equals(absence.getCompensationPolicy());
            boolean activeWorkflow = consumesBalance(absence.getStatus());
            OvertimeUsage linked = usageByAbsence.get(absence.getId());
            TimeLedgerEntry latestAudit = entries
                    .findFirstByOwnerAndSourceKindAndSourceIdOrderByIdDesc(user, "ABSENCE", absence.getId())
                    .filter(item -> "RESERVED".equals(item.getPostingState()) || "POSTED".equals(item.getPostingState()))
                    .orElse(null);

            if (overtimeBacked && activeWorkflow && linked == null) {
                issues.add(issue("MISSING_LINKED_USAGE", "ERROR",
                        "Отгул из банка переработок не имеет связанного списания", "ABSENCE", absence.getId()));
            }
            if (overtimeBacked && activeWorkflow && latestAudit == null) {
                issues.add(issue("MISSING_ACTIVE_AUDIT", "ERROR",
                        "Активный отгул не имеет текущей записи аудита", "ABSENCE", absence.getId()));
            }
            if (latestAudit != null && !activeWorkflow) {
                issues.add(issue("INACTIVE_ABSENCE_HAS_ACTIVE_AUDIT", "ERROR",
                        "Неактивное отсутствие имеет незакрытую запись аудита", "ABSENCE", absence.getId()));
            }
            if (latestAudit != null && activeWorkflow) {
                String expectedState = usagePostingState(absence.getStatus());
                if (!expectedState.equals(latestAudit.getPostingState())) {
                    issues.add(issue("AUDIT_STATE_MISMATCH", "ERROR",
                            "Состояние аудита не совпадает со статусом отсутствия", "ABSENCE", absence.getId()));
                }
                int expectedAuditMinutes = overtimeBacked
                        ? -Math.max(0, absence.getCompensatedMinutes()) : 0;
                if (latestAudit.getSignedMinutes() != expectedAuditMinutes) {
                    issues.add(issue("AUDIT_MINUTES_MISMATCH", "ERROR",
                            "Минуты аудита не совпадают с отсутствием", "ABSENCE", absence.getId()));
                }
            }
        }

        long openingCredits = credits.findByOwnerOrderByWorkDateAscIdAsc(user).stream()
                .filter(item -> item.getReason() != null && item.getReason().contains("перенос в единый банк V43"))
                .count();
        if (openingCredits > 1) {
            issues.add(issue("DUPLICATE_OPENING_CREDIT", "ERROR",
                    "Начальный баланс V43 перенесён больше одного раза", "OVERTIME_CREDIT", null));
        }

        List<TimeLedgerEntry> audit = entries
                .findByOwnerAndEffectiveDateBetweenOrderByEffectiveDateAscIdAsc(user, from, to);
        int reserved = audit.stream().filter(item -> "RESERVED".equals(item.getPostingState()))
                .mapToInt(item -> Math.max(0, -item.getSignedMinutes())).sum();
        int posted = audit.stream().filter(item -> "POSTED".equals(item.getPostingState()))
                .mapToInt(item -> Math.max(0, -item.getSignedMinutes())).sum();
        int reversed = audit.stream().filter(item -> "REVERSED".equals(item.getPostingState()))
                .mapToInt(item -> Math.max(0, item.getSignedMinutes())).sum();

        LocalDate monthFrom = YearMonth.from(from).atDay(1);
        LocalDate monthTo = YearMonth.from(to).atDay(1);
        List<AccountingPeriodDto> periodDtos = periods
                .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAsc(user, monthFrom, monthTo)
                .stream().map(this::toDto).toList();
        return new LedgerIntegrityDto(from.toString(), to.toString(), issues.isEmpty(), reserved, posted, reversed,
                orphan, allocationMismatch, List.copyOf(issues), audit.stream().map(this::toDto).toList(), periodDtos);
    }

    public record AbsenceLedgerSnapshot(String status, String compensationPolicy, int compensatedMinutes,
                                        LocalDate effectiveDate) {}

    private LedgerIntegrityIssueDto issue(String code, String severity, String message, String sourceKind, Long sourceId) {
        return new LedgerIntegrityIssueDto(code, severity, message, sourceKind, sourceId);
    }

    private AccountingPeriodDto toDto(TimeAccountingPeriod period) {
        return new AccountingPeriodDto(period.getPeriodMonth().toString().substring(0, 7), period.getStatus(),
                period.getClosedAt() == null ? null : period.getClosedAt().toString(),
                period.getUpdatedAt() == null ? null : period.getUpdatedAt().toString());
    }

    private TimeLedgerEntryDto toDto(TimeLedgerEntry entry) {
        return new TimeLedgerEntryDto(entry.getId(), entry.getEntryKind(), entry.getSourceKind(), entry.getSourceId(),
                entry.getEffectiveDate().toString(), entry.getSignedMinutes(), entry.getPostingState(),
                entry.getReversalOf() == null ? null : entry.getReversalOf().getId(), entry.getReason(),
                entry.getCreatedAt() == null ? null : entry.getCreatedAt().toString());
    }

    private YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value); }
        catch (Exception ex) { throw ApiException.badRequest("Месяц должен быть в формате yyyy-MM"); }
    }

    private String absenceReason(AbsencePeriod period, String action) {
        String title = period.getTitle() == null || period.getTitle().isBlank()
                ? period.getType().getName() : period.getTitle();
        return title + " · " + safe(action) + " · " + period.getStatus();
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return "Корректировка закрытого периода";
        return value.trim();
    }

    private String safe(String value) { return value == null || value.isBlank() ? "UPDATE" : value; }
}
