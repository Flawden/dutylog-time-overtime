package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Authoritative absence layer: the planned shift remains intact while a full-day
 * absence may become the factual visual state of the day. Vacation days and
 * compensatory time-off hours use independent balance policies.
 */
@Service
public class VacationPlannerService {
    public static final int MAX_PERIOD_DAYS = 400;
    public static final List<Integer> DURATION_PRESETS = List.of(14, 28, 35);
    public static final String VACATION_DAYS = "VACATION_DAYS";
    public static final String TIME_OFF_HOURS = "TIME_OFF_HOURS";
    public static final String NO_BALANCE = "NONE";
    public static final String FULL_DAY = "FULL_DAY";
    public static final String PARTIAL = "PARTIAL";
    public static final String VACATION_ALLOWANCE = "VACATION_ALLOWANCE";
    public static final String OVERTIME_BANK = "OVERTIME_BANK";
    public static final String SICK_PAY = "SICK_PAY";
    public static final String UNPAID = "UNPAID";
    public static final String NO_COMPENSATION = "NONE";

    private final VacationSettingsRepository settingsRepository;
    private final AbsenceTypeRepository typeRepository;
    private final AbsencePeriodRepository periodRepository;
    private final DayEntryRepository dayRepository;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;
    private final OvertimeService overtimeService;

    public VacationPlannerService(VacationSettingsRepository settingsRepository,
                                  AbsenceTypeRepository typeRepository,
                                  AbsencePeriodRepository periodRepository,
                                  DayEntryRepository dayRepository,
                                  DayEntryService dayEntryService,
                                  UserTimeService userTimeService,
                                  SecurityEventLogger securityEvents,
                                  OvertimeService overtimeService) {
        this.settingsRepository = settingsRepository;
        this.typeRepository = typeRepository;
        this.periodRepository = periodRepository;
        this.dayRepository = dayRepository;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
        this.overtimeService = overtimeService;
    }

    @Transactional
    public VacationPlannerDto planner(AppUser user, LocalDate referenceDate, LocalDate from, LocalDate to) {
        VacationSettings settings = ensureSettings(user);
        ensureDefaultTypes(user);
        LocalDate reference = referenceDate == null ? userTimeService.today(user) : referenceDate;
        WorkYear year = workYearContaining(settings, reference);
        LocalDate rangeFrom = from == null ? year.start() : from;
        LocalDate rangeTo = to == null ? year.end() : to;
        dayEntryService.validateRange(rangeFrom, rangeTo);
        List<AbsencePeriod> periods = periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, rangeFrom, rangeTo);
        List<AbsenceOccurrenceDto> occurrences = occurrences(user, settings, rangeFrom, rangeTo);
        return new VacationPlannerDto(
                VacationSettingsDto.from(settings),
                summary(user, settings, reference, null),
                DURATION_PRESETS,
                typeRepository.findByOwnerOrderBySortOrderAscIdAsc(user).stream().map(AbsenceTypeDto::from).toList(),
                periods.stream().map(period -> toPeriodDto(settings, period)).toList(),
                occurrences,
                summarizeOccurrences(occurrences)
        );
    }

    @Transactional
    public VacationSettingsDto updateSettings(AppUser user, VacationSettingsUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = lockSettings(user);
        if (req.annualAllowanceDays() != null) settings.setAnnualAllowanceDays(req.annualAllowanceDays());
        if (req.carryoverDays() != null) settings.setCarryoverDays(req.carryoverDays());
        if (req.countMode() != null) settings.setCountMode(normalizeCountMode(req.countMode()));
        if (req.workYearStartMonth() != null) settings.setWorkYearStartMonth(req.workYearStartMonth());
        if (req.workYearStartDay() != null) settings.setWorkYearStartDay(req.workYearStartDay());
        // v27.26: the canonical compensatory balance lives in the overtime ledger.
        // Keep the deprecated request field wire-compatible, but never recreate a parallel mutable balance.
        if (req.defaultTimeOffDayHours() != null) settings.setDefaultTimeOffDayMinutes(hoursToMinutes(req.defaultTimeOffDayHours()));
        validateSettings(settings);
        validateAllStoredBalances(user, settings);
        return VacationSettingsDto.from(settingsRepository.saveAndFlush(settings));
    }

    @Transactional
    public List<AbsenceTypeDto> types(AppUser user) {
        ensureDefaultTypes(user);
        return typeRepository.findByOwnerOrderBySortOrderAscIdAsc(user).stream().map(AbsenceTypeDto::from).toList();
    }

    @Transactional
    public AbsenceTypeDto createType(AppUser user, AbsenceTypeCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        ensureDefaultTypes(user);
        String name = cleanName(req.name());
        ensureUniqueTypeName(user, name, null);
        AbsenceType type = new AbsenceType(user);
        type.setName(name);
        type.setColor(normalizeColor(req.color(), "#4FA3A5"));
        type.setBalancePolicy(normalizeBalancePolicy(req.balancePolicy(), Boolean.TRUE.equals(req.countsAgainstAllowance())));
        type.setCountsAgainstAllowance(VACATION_DAYS.equals(type.getBalancePolicy()));
        type.setFullDayReplacesShift(req.fullDayReplacesShift() == null || req.fullDayReplacesShift());
        type.setSystemPreset(false);
        type.setSystemCode(null);
        type.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        return AbsenceTypeDto.from(typeRepository.saveAndFlush(type));
    }

    @Transactional
    public AbsenceTypeDto updateType(AppUser user, Long id, AbsenceTypeUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        lockSettings(user);
        AbsenceType type = requireOwnedType(user, id);
        boolean semanticChange = req.name() != null || req.countsAgainstAllowance() != null
                || req.balancePolicy() != null || req.fullDayReplacesShift() != null;
        if (type.isSystemPreset() && semanticChange) {
            throw ApiException.conflict("Встроенный тип можно перекрасить и переместить, но нельзя менять его смысл");
        }
        if (req.name() != null) {
            String name = cleanName(req.name());
            ensureUniqueTypeName(user, name, type.getId());
            type.setName(name);
        }
        if (req.color() != null) type.setColor(normalizeColor(req.color(), type.getColor()));
        String requestedPolicy = req.balancePolicy();
        if (requestedPolicy == null && req.countsAgainstAllowance() != null) {
            requestedPolicy = req.countsAgainstAllowance() ? VACATION_DAYS : NO_BALANCE;
        }
        if (requestedPolicy != null) {
            requestedPolicy = normalizeBalancePolicy(requestedPolicy, false);
            if (!requestedPolicy.equals(type.getBalancePolicy()) && periodRepository.existsByType(type)) {
                throw ApiException.conflict("ABSENCE_TYPE_IN_USE", "Нельзя менять балансную политику используемого типа");
            }
            type.setBalancePolicy(requestedPolicy);
            type.setCountsAgainstAllowance(VACATION_DAYS.equals(requestedPolicy));
        }
        if (req.fullDayReplacesShift() != null) type.setFullDayReplacesShift(req.fullDayReplacesShift());
        if (req.sortOrder() != null) type.setSortOrder(req.sortOrder());
        return AbsenceTypeDto.from(typeRepository.saveAndFlush(type));
    }

    @Transactional
    public void deleteType(AppUser user, Long id) {
        lockSettings(user);
        AbsenceType type = requireOwnedType(user, id);
        if (type.isSystemPreset()) throw ApiException.conflict("Встроенный тип отсутствия нельзя удалить");
        if (periodRepository.existsByType(type)) throw ApiException.conflict("Тип используется в периодах отсутствия");
        typeRepository.delete(type);
    }

    @Transactional
    public AbsencePreviewDto preview(AppUser user, AbsencePreviewRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = ensureSettings(user);
        ensureDefaultTypes(user);
        AbsenceType type = requireOwnedType(user, req.typeId());
        AbsenceShape shape = parseShape(req.startDate(), req.endDate(), req.coverage(), req.startTime(), req.endTime());
        String compensationPolicy = resolveCompensationPolicy(type, req.compensationPolicy(), null);
        return buildPreview(user, settings, type, shape, req.excludePeriodId(), compensationPolicy);
    }

    @Transactional
    public AbsencePeriodDto createPeriod(AppUser user, AbsencePeriodCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = lockSettings(user);
        ensureDefaultTypes(user);
        AbsenceType type = requireOwnedType(user, req.typeId());
        AbsenceShape shape = parseShape(req.startDate(), req.endDate(), req.coverage(), req.startTime(), req.endTime());
        validateNoOverlap(user, shape, null);
        String compensationPolicy = resolveCompensationPolicy(type, req.compensationPolicy(), null);
        int chargedMinutes = calculateChargedMinutes(user, settings, type, shape, compensationPolicy);
        validateBalances(user, settings, type, shape.range(), chargedMinutes, null, compensationPolicy);

        AbsencePeriod period = new AbsencePeriod(user);
        period.setType(type);
        period.setTitle(normalizeOptional(req.title()));
        applyShape(period, shape);
        period.setChargedMinutes(chargedMinutes);
        period.setCompensationPolicy(compensationPolicy);
        period.setCompensatedMinutes(OVERTIME_BANK.equals(compensationPolicy) ? chargedMinutes : 0);
        period.setStatus(normalizeStatus(req.status()));
        period.setNote(normalizeOptional(req.note()));
        period = periodRepository.saveAndFlush(period);
        syncLinkedOvertimeUsage(user, period);
        return toPeriodDto(settings, period);
    }

    @Transactional
    public AbsencePeriodDto updatePeriod(AppUser user, Long id, AbsencePeriodUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = lockSettings(user);
        AbsencePeriod period = requireOwnedPeriod(user, id);
        AbsenceType type = req.typeId() == null ? period.getType() : requireOwnedType(user, req.typeId());
        String coverage = req.coverage() == null ? period.getCoverage() : req.coverage();
        String start = req.startDate() == null ? period.getStartDate().toString() : req.startDate();
        String end = req.endDate() == null ? period.getEndDate().toString() : req.endDate();
        String startTime = Boolean.TRUE.equals(req.clearTimes()) ? null
                : req.startTime() != null ? req.startTime() : timeString(period.getStartTime());
        String endTime = Boolean.TRUE.equals(req.clearTimes()) ? null
                : req.endTime() != null ? req.endTime() : timeString(period.getEndTime());
        AbsenceShape shape = parseShape(start, end, coverage, startTime, endTime);
        validateNoOverlap(user, shape, id);
        String compensationPolicy = resolveCompensationPolicy(type, req.compensationPolicy(),
                req.typeId() == null ? period.getCompensationPolicy() : null);
        int chargedMinutes = calculateChargedMinutes(user, settings, type, shape, compensationPolicy);
        validateBalances(user, settings, type, shape.range(), chargedMinutes, id, compensationPolicy);

        period.setType(type);
        applyShape(period, shape);
        period.setChargedMinutes(chargedMinutes);
        period.setCompensationPolicy(compensationPolicy);
        period.setCompensatedMinutes(OVERTIME_BANK.equals(compensationPolicy) ? chargedMinutes : 0);
        if (Boolean.TRUE.equals(req.clearTitle())) period.setTitle(null);
        else if (req.title() != null) period.setTitle(normalizeOptional(req.title()));
        if (Boolean.TRUE.equals(req.clearNote())) period.setNote(null);
        else if (req.note() != null) period.setNote(normalizeOptional(req.note()));
        if (req.status() != null) period.setStatus(normalizeStatus(req.status()));
        period = periodRepository.saveAndFlush(period);
        syncLinkedOvertimeUsage(user, period);
        return toPeriodDto(settings, period);
    }

    @Transactional
    public void deletePeriod(AppUser user, Long id) {
        AbsencePeriod period = requireOwnedPeriod(user, id);
        overtimeService.deleteLinkedAbsenceUsage(user, period.getId());
        periodRepository.delete(period);
    }

    @Transactional
    public List<AbsenceOccurrenceDto> occurrences(AppUser user, LocalDate from, LocalDate to) {
        return occurrences(user, ensureSettings(user), from, to);
    }

    private List<AbsenceOccurrenceDto> occurrences(AppUser user, VacationSettings settings, LocalDate from, LocalDate to) {
        List<AbsencePeriod> periods = periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, from, to);
        Map<LocalDate, ShiftPlan> plans = shiftPlans(user, from, to);
        List<AbsenceOccurrenceDto> out = new ArrayList<>();
        for (AbsencePeriod period : periods) {
            LocalDate visibleFrom = period.getStartDate().isBefore(from) ? from : period.getStartDate();
            LocalDate visibleTo = period.getEndDate().isAfter(to) ? to : period.getEndDate();
            for (LocalDate date = visibleFrom; !date.isAfter(visibleTo); date = date.plusDays(1)) {
                AbsenceType type = period.getType();
                ShiftPlan plan = plans.get(date);
                boolean replaces = FULL_DAY.equals(period.getCoverage()) && type.isFullDayReplacesShift();
                out.add(new AbsenceOccurrenceDto(
                        period.getId(), type.getId(), type.getName(), type.getColor(), type.getSystemCode(),
                        period.getTitle(), date.toString(), period.getStartDate().toString(), period.getEndDate().toString(),
                        period.getStatus(), VACATION_DAYS.equals(type.getBalancePolicy()) && countedByMode(settings, date),
                        plan != null, type.getBalancePolicy(), period.getCoverage(), timeString(period.getStartTime()),
                        timeString(period.getEndTime()), period.getChargedMinutes(), replaces,
                        plan == null ? null : plan.name(), plan == null ? null : plan.color(), plan == null ? 0 : plan.minutes(),
                        period.getCompensationPolicy(), period.getCompensatedMinutes(),
                        overtimeService.linkedUsageId(user, period.getId())
                ));
            }
        }
        return out;
    }

    @Transactional
    public VacationSettings ensureSettings(AppUser user) {
        return settingsRepository.findByOwner(user)
                .orElseGet(() -> settingsRepository.saveAndFlush(new VacationSettings(user)));
    }

    private VacationSettings lockSettings(AppUser user) {
        VacationSettings settings = ensureSettings(user);
        return settingsRepository.findForUpdateByOwner(user).orElse(settings);
    }

    @Transactional
    public void ensureDefaultTypes(AppUser user) {
        addDefaultType(user, "VACATION", "Отпуск", "#4FA3A5", VACATION_DAYS, true, 10);
        addDefaultType(user, "TIME_OFF", "Отгул", "#4A90E2", TIME_OFF_HOURS, true, 20);
        addDefaultType(user, "SICK", "Больничный", "#E0653A", NO_BALANCE, true, 30);
        addDefaultType(user, "UNPAID", "Без содержания", "#8B929E", NO_BALANCE, true, 40);
        addDefaultType(user, "OTHER", "Другое", "#9B7BE0", NO_BALANCE, true, 50);
        typeRepository.flush();
    }

    public AbsenceType requireOwnedType(AppUser user, Long id) {
        AbsenceType type = typeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Тип отсутствия не найден"));
        if (!type.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected", "resource=absence_type id=" + id);
            throw ApiException.notFound("Тип отсутствия не найден");
        }
        return type;
    }

    public AbsencePeriod requireOwnedPeriod(AppUser user, Long id) {
        AbsencePeriod period = periodRepository.findById(id).orElseThrow(() -> ApiException.notFound("Период отсутствия не найден"));
        if (!period.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected", "resource=absence_period id=" + id);
            throw ApiException.notFound("Период отсутствия не найден");
        }
        return period;
    }

    private AbsencePreviewDto buildPreview(AppUser user, VacationSettings settings, AbsenceType type,
                                            AbsenceShape shape, Long excludePeriodId,
                                            String compensationPolicy) {
        DateRange range = shape.range();
        List<AbsencePeriod> overlaps = overlappingPeriods(user, shape, excludePeriodId);
        Map<LocalDate, ShiftPlan> plans = shiftPlans(user, range.from(), range.to());
        List<AbsencePreviewItemDto> items = new ArrayList<>();
        int counted = 0;
        int shiftConflicts = 0;
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            LocalDate previewDate = date;
            boolean countedDay = VACATION_DAYS.equals(type.getBalancePolicy()) && countedByMode(settings, previewDate);
            ShiftPlan plan = plans.get(previewDate);
            AbsencePeriod existing = overlaps.stream().filter(period -> covers(period, previewDate)).findFirst().orElse(null);
            if (countedDay) counted++;
            if (plan != null) shiftConflicts++;
            items.add(new AbsencePreviewItemDto(
                    previewDate.toString(), isWeekend(previewDate), countedDay, plan != null,
                    existing == null ? null : existing.getId(), existing == null ? null : displayTitle(existing),
                    existing == null ? "APPLY" : "CONFLICT",
                    plan == null ? null : plan.name(), plan == null ? null : plan.color(), plan == null ? 0 : plan.minutes(),
                    FULL_DAY.equals(shape.coverage()) && type.isFullDayReplacesShift()
            ));
        }

        AllowanceProjection critical = mostConstrainedProjection(user, settings, type, range, excludePeriodId);
        int charged = calculateChargedMinutes(user, settings, type, shape, compensationPolicy);
        int timeOffAvailable = overtimeService.totalEarnedMinutes(user);
        int editableCapacity = overtimeService.availableMinutesForAbsence(user, excludePeriodId);
        int timeOffBefore = timeOffAvailable - editableCapacity;
        int timeOffProjected = timeOffBefore + (OVERTIME_BANK.equals(compensationPolicy) ? charged : 0);
        int timeOffRemaining = timeOffAvailable - timeOffProjected;
        boolean vacationExceeded = VACATION_DAYS.equals(type.getBalancePolicy()) && critical.remaining() < 0;
        boolean timeOffExceeded = OVERTIME_BANK.equals(compensationPolicy) && timeOffRemaining < 0;
        boolean exceeded = vacationExceeded || timeOffExceeded;
        int exceededBy = vacationExceeded ? Math.max(0, -critical.remaining()) : Math.max(0, -timeOffRemaining);
        return new AbsencePreviewDto(
                type.getId(), type.getName(), range.from().toString(), range.to().toString(), range.days(), counted,
                shiftConflicts, overlaps.size(), critical.year().start().toString(), critical.year().end().toString(),
                critical.available(), critical.plannedBefore(), critical.projected(), critical.remaining(),
                exceeded, exceededBy, items, type.getBalancePolicy(), shape.coverage(), charged,
                timeOffAvailable, timeOffBefore, timeOffProjected, timeOffRemaining, compensationPolicy
        );
    }

    private VacationSummaryDto summary(AppUser user, VacationSettings settings, LocalDate reference, Long excludePeriodId) {
        WorkYear year = workYearContaining(settings, reference);
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        int planned = plannedDays(user, settings, year, excludePeriodId);
        int timeOffAvailable = overtimeService.totalEarnedMinutes(user);
        int timeOffUsed = overtimeService.totalUsedMinutes(user);
        return new VacationSummaryDto(year.start().toString(), year.end().toString(),
                settings.getAnnualAllowanceDays(), settings.getCarryoverDays(), available, planned,
                available - planned, settings.getCountMode(), timeOffAvailable,
                timeOffUsed, Math.max(0, timeOffAvailable - timeOffUsed));
    }

    private AllowanceProjection mostConstrainedProjection(AppUser user, VacationSettings settings, AbsenceType type,
                                                          DateRange requested, Long excludePeriodId) {
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        WorkYear year = workYearContaining(settings, requested.from());
        AllowanceProjection critical = null;
        while (!year.start().isAfter(requested.to())) {
            int plannedBefore = plannedDays(user, settings, year, excludePeriodId);
            int added = VACATION_DAYS.equals(type.getBalancePolicy())
                    ? countIntersection(settings, requested.from(), requested.to(), year.start(), year.end()) : 0;
            AllowanceProjection candidate = new AllowanceProjection(
                    year, available, plannedBefore, plannedBefore + added, available - plannedBefore - added);
            if (critical == null || candidate.remaining() < critical.remaining()) critical = candidate;
            year = new WorkYear(year.start().plusYears(1), year.end().plusYears(1));
        }
        return Objects.requireNonNull(critical);
    }

    private int plannedDays(AppUser user, VacationSettings settings, WorkYear year, Long excludePeriodId) {
        int total = 0;
        for (AbsencePeriod period : periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, year.start(), year.end())) {
            if (Objects.equals(period.getId(), excludePeriodId) || !VACATION_DAYS.equals(period.getType().getBalancePolicy())) continue;
            total += countIntersection(settings, period.getStartDate(), period.getEndDate(), year.start(), year.end());
        }
        return total;
    }

    private int timeOffPlannedMinutes(AppUser user, Long excludePeriodId) {
        return periodRepository.findByOwnerOrderByStartDateAscIdAsc(user).stream()
                .filter(period -> !Objects.equals(period.getId(), excludePeriodId))
                .filter(period -> TIME_OFF_HOURS.equals(period.getType().getBalancePolicy()))
                .mapToInt(AbsencePeriod::getChargedMinutes).sum();
    }

    private void validateAllStoredBalances(AppUser user, VacationSettings settings) {
        validateAllStoredWorkYears(user, settings);
    }

    /** Keeps the original vacation work-year invariant as a named contract while V42 adds hour balances. */
    private void validateAllStoredWorkYears(AppUser user, VacationSettings settings) {
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        Set<WorkYear> years = new LinkedHashSet<>();
        for (AbsencePeriod period : periodRepository.findByOwnerOrderByStartDateAscIdAsc(user)) {
            if (!VACATION_DAYS.equals(period.getType().getBalancePolicy())) continue;
            WorkYear year = workYearContaining(settings, period.getStartDate());
            while (!year.start().isAfter(period.getEndDate())) {
                years.add(year);
                year = new WorkYear(year.start().plusYears(1), year.end().plusYears(1));
            }
        }
        for (WorkYear year : years) {
            if (plannedDays(user, settings, year, null) > available) {
                throw ApiException.conflict("VACATION_LIMIT_EXCEEDED",
                        "Новые правила уменьшают доступный отпуск ниже уже запланированного количества дней в рабочем году "
                                + year.start() + " — " + year.end());
            }
        }
    }

    private void validateBalances(AppUser user, VacationSettings settings, AbsenceType type,
                                  DateRange requested, int chargedMinutes, Long excludePeriodId,
                                  String compensationPolicy) {
        if (VACATION_DAYS.equals(type.getBalancePolicy())) {
            WorkYear year = workYearContaining(settings, requested.from());
            while (!year.start().isAfter(requested.to())) {
                int existing = plannedDays(user, settings, year, excludePeriodId);
                int added = countIntersection(settings, requested.from(), requested.to(), year.start(), year.end());
                int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
                if (existing + added > available) {
                    int exceeded = existing + added - available;
                    throw ApiException.conflict("VACATION_LIMIT_EXCEEDED",
                            "Недостаточно дней отпуска в рабочем году " + year.start() + " — " + year.end()
                                    + ": превышение на " + exceeded + " дн.");
                }
                year = new WorkYear(year.start().plusYears(1), year.end().plusYears(1));
            }
        }
        if (OVERTIME_BANK.equals(compensationPolicy)) {
            int available = overtimeService.availableMinutesForAbsence(user, excludePeriodId);
            if (chargedMinutes > available) {
                throw ApiException.conflict("OVERTIME_BALANCE_EXCEEDED",
                        "Недостаточно ранее заработанного времени: доступно " + available
                                + " мин., требуется " + chargedMinutes + " мин.");
            }
        }
    }

    private void validateNoOverlap(AppUser user, AbsenceShape shape, Long excludePeriodId) {
        if (!overlappingPeriods(user, shape, excludePeriodId).isEmpty()) {
            throw ApiException.conflict("ABSENCE_OVERLAP", "Период пересекается с другим отсутствием");
        }
    }

    private List<AbsencePeriod> overlappingPeriods(AppUser user, AbsenceShape shape, Long excludePeriodId) {
        return periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                        user, shape.range().from(), shape.range().to())
                .stream()
                .filter(period -> !Objects.equals(period.getId(), excludePeriodId))
                .filter(period -> overlaps(period, shape))
                .toList();
    }

    private boolean overlaps(AbsencePeriod existing, AbsenceShape requested) {
        if (FULL_DAY.equals(existing.getCoverage()) || FULL_DAY.equals(requested.coverage())) return true;
        if (!existing.getStartDate().equals(requested.range().from())) return false;
        return requested.startTime().isBefore(existing.getEndTime()) && requested.endTime().isAfter(existing.getStartTime());
    }

    private boolean covers(AbsencePeriod period, LocalDate date) {
        return !date.isBefore(period.getStartDate()) && !date.isAfter(period.getEndDate());
    }

    private AbsencePeriodDto toPeriodDto(VacationSettings settings, AbsencePeriod period) {
        int calendarDays = (int) ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        int countedDays = VACATION_DAYS.equals(period.getType().getBalancePolicy())
                ? countDays(settings, period.getStartDate(), period.getEndDate()) : 0;
        int shiftConflicts = shiftPlans(period.getOwner(), period.getStartDate(), period.getEndDate()).size();
        AbsenceType type = period.getType();
        return new AbsencePeriodDto(
                period.getId(), type.getId(), type.getName(), type.getColor(), type.getSystemCode(),
                type.isCountsAgainstAllowance(), period.getTitle(), period.getStartDate().toString(), period.getEndDate().toString(),
                period.getStatus(), period.getNote(), calendarDays, countedDays, shiftConflicts,
                period.getCreatedAt() == null ? null : period.getCreatedAt().toString(),
                period.getUpdatedAt() == null ? null : period.getUpdatedAt().toString(),
                type.getBalancePolicy(), period.getCoverage(), timeString(period.getStartTime()), timeString(period.getEndTime()),
                period.getChargedMinutes(), FULL_DAY.equals(period.getCoverage()) && type.isFullDayReplacesShift(),
                period.getCompensationPolicy(), period.getCompensatedMinutes(),
                overtimeService.linkedUsageId(period.getOwner(), period.getId())
        );
    }

    private Map<LocalDate, ShiftPlan> shiftPlans(AppUser user, LocalDate from, LocalDate to) {
        Map<LocalDate, ShiftPlan> plans = new LinkedHashMap<>();
        for (DayEntry entry : dayRepository.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            ShiftType shift = entry.getShiftType();
            if (shift == null) continue;
            int minutes = entry.getShiftNetMinutes() > 0 ? Math.toIntExact(entry.getShiftNetMinutes())
                    : Math.max(0, (int) Math.round(shift.effectivePlannedHours() * 60.0));
            plans.put(entry.getDate(), new ShiftPlan(shift.getName(), shift.getColor(), minutes));
        }
        return plans;
    }

    private String resolveCompensationPolicy(AbsenceType type, String requested, String current) {
        String policy = requested == null || requested.isBlank()
                ? (current == null || current.isBlank() ? defaultCompensationPolicy(type) : current)
                : requested.trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of(VACATION_ALLOWANCE, OVERTIME_BANK, SICK_PAY, UNPAID, NO_COMPENSATION);
        if (!allowed.contains(policy)) throw ApiException.badRequest("Некорректный источник компенсации");
        if (VACATION_DAYS.equals(type.getBalancePolicy()) && !VACATION_ALLOWANCE.equals(policy)) {
            throw ApiException.badRequest("Оплачиваемый отпуск покрывается только отпускным балансом");
        }
        if (TIME_OFF_HOURS.equals(type.getBalancePolicy()) && !OVERTIME_BANK.equals(policy)) {
            throw ApiException.badRequest("Отгул за ранее отработанное время покрывается только банком переработок");
        }
        if (VACATION_ALLOWANCE.equals(policy) && !VACATION_DAYS.equals(type.getBalancePolicy())) {
            throw ApiException.badRequest("Отпускной баланс доступен только отпускным типам");
        }
        if ("SICK".equals(type.getSystemCode()) && !SICK_PAY.equals(policy)) {
            throw ApiException.badRequest("Больничный использует больничную политику оплаты");
        }
        if ("UNPAID".equals(type.getSystemCode()) && !UNPAID.equals(policy)) {
            throw ApiException.badRequest("Отсутствие без содержания должно оставаться неоплачиваемым");
        }
        return policy;
    }

    private String defaultCompensationPolicy(AbsenceType type) {
        if (VACATION_DAYS.equals(type.getBalancePolicy())) return VACATION_ALLOWANCE;
        if (TIME_OFF_HOURS.equals(type.getBalancePolicy())) return OVERTIME_BANK;
        if ("SICK".equals(type.getSystemCode())) return SICK_PAY;
        if ("UNPAID".equals(type.getSystemCode())) return UNPAID;
        return NO_COMPENSATION;
    }

    private void syncLinkedOvertimeUsage(AppUser user, AbsencePeriod period) {
        if (OVERTIME_BANK.equals(period.getCompensationPolicy())) {
            Long usageId = overtimeService.upsertLinkedAbsenceUsage(
                    user,
                    period.getId(),
                    period.getStartDate(),
                    period.getCompensatedMinutes(),
                    "Отгул: " + displayTitle(period)
            );
            if (usageId == null) throw new IllegalStateException("Linked overtime usage was not created");
        } else {
            overtimeService.deleteLinkedAbsenceUsage(user, period.getId());
        }
    }

    private int calculateChargedMinutes(AppUser user, VacationSettings settings, AbsenceType type, AbsenceShape shape,
                                        String compensationPolicy) {
        if (!OVERTIME_BANK.equals(compensationPolicy)) return 0;
        if (PARTIAL.equals(shape.coverage())) {
            return Math.toIntExact(Duration.between(shape.startTime(), shape.endTime()).toMinutes());
        }
        Map<LocalDate, ShiftPlan> plans = shiftPlans(user, shape.range().from(), shape.range().to());
        int total = 0;
        for (LocalDate date = shape.range().from(); !date.isAfter(shape.range().to()); date = date.plusDays(1)) {
            ShiftPlan plan = plans.get(date);
            total += plan != null && plan.minutes() > 0 ? plan.minutes() : settings.getDefaultTimeOffDayMinutes();
        }
        return total;
    }

    private List<AbsenceTypeSummaryDto> summarizeOccurrences(List<AbsenceOccurrenceDto> occurrences) {
        Map<Long, MutableTypeSummary> grouped = new LinkedHashMap<>();
        for (AbsenceOccurrenceDto item : occurrences) {
            MutableTypeSummary summary = grouped.computeIfAbsent(item.typeId(), ignored ->
                    new MutableTypeSummary(item.typeId(), item.typeName(), item.typeColor(), item.systemCode(), item.balancePolicy()));
            if (FULL_DAY.equals(item.coverage())) summary.fullDays++;
            else summary.partialMinutes += partialMinutes(item.startTime(), item.endTime());
        }
        Set<Long> chargedPeriods = new HashSet<>();
        for (AbsenceOccurrenceDto item : occurrences) {
            if (chargedPeriods.add(item.periodId())) grouped.get(item.typeId()).chargedMinutes += item.chargedMinutes();
        }
        return grouped.values().stream().map(value -> new AbsenceTypeSummaryDto(
                value.typeId, value.typeName, value.typeColor, value.systemCode, value.balancePolicy,
                value.fullDays, value.partialMinutes, value.chargedMinutes)).toList();
    }

    private int partialMinutes(String start, String end) {
        if (start == null || end == null) return 0;
        return Math.toIntExact(Duration.between(LocalTime.parse(start), LocalTime.parse(end)).toMinutes());
    }

    private AbsenceShape parseShape(String from, String to, String coverageValue, String startTimeValue, String endTimeValue) {
        DateRange range = validateRange(
                parseDate(from, "Дата начала должна быть в формате yyyy-MM-dd"),
                parseDate(to, "Дата окончания должна быть в формате yyyy-MM-dd"));
        String coverage = normalizeCoverage(coverageValue);
        if (FULL_DAY.equals(coverage)) return new AbsenceShape(range, coverage, null, null);
        if (!range.from().equals(range.to())) throw ApiException.badRequest("Частичный отгул должен находиться в пределах одного дня");
        LocalTime start = parseTime(startTimeValue, "Укажите время начала частичного отсутствия");
        LocalTime end = parseTime(endTimeValue, "Укажите время окончания частичного отсутствия");
        if (!end.isAfter(start)) throw ApiException.badRequest("Время окончания должно быть позже времени начала");
        return new AbsenceShape(range, coverage, start, end);
    }

    private void applyShape(AbsencePeriod period, AbsenceShape shape) {
        period.setStartDate(shape.range().from());
        period.setEndDate(shape.range().to());
        period.setCoverage(shape.coverage());
        period.setStartTime(shape.startTime());
        period.setEndTime(shape.endTime());
    }

    private int countIntersection(VacationSettings settings, LocalDate firstStart, LocalDate firstEnd,
                                  LocalDate secondStart, LocalDate secondEnd) {
        LocalDate start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        LocalDate end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return start.isAfter(end) ? 0 : countDays(settings, start, end);
    }

    private int countDays(VacationSettings settings, LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) if (countedByMode(settings, date)) count++;
        return count;
    }

    private boolean countedByMode(VacationSettings settings, LocalDate date) {
        return !"WEEKDAYS".equals(settings.getCountMode()) || !isWeekend(date);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private WorkYear workYearContaining(VacationSettings settings, LocalDate reference) {
        LocalDate candidate = LocalDate.of(reference.getYear(), settings.getWorkYearStartMonth(), settings.getWorkYearStartDay());
        if (reference.isBefore(candidate)) candidate = candidate.minusYears(1);
        return new WorkYear(candidate, candidate.plusYears(1).minusDays(1));
    }

    private DateRange validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw ApiException.badRequest("Дата окончания не может быть раньше даты начала");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_PERIOD_DAYS) throw ApiException.badRequest("Период отсутствия: максимум " + MAX_PERIOD_DAYS + " дней");
        return new DateRange(from, to, (int) days);
    }

    private LocalDate parseDate(String value, String message) { return dayEntryService.parseDate(value, message); }

    private LocalTime parseTime(String value, String message) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return LocalTime.parse(value.trim());
        } catch (RuntimeException ex) {
            throw ApiException.badRequest(message);
        }
    }

    private void validateSettings(VacationSettings settings) {
        if (settings.getAnnualAllowanceDays() < 0 || settings.getAnnualAllowanceDays() > 366)
            throw ApiException.badRequest("Годовая норма должна быть от 0 до 366 дней");
        if (settings.getCarryoverDays() < 0 || settings.getCarryoverDays() > 366)
            throw ApiException.badRequest("Перенос должен быть от 0 до 366 дней");
        normalizeCountMode(settings.getCountMode());
        if (settings.getWorkYearStartMonth() < 1 || settings.getWorkYearStartMonth() > 12)
            throw ApiException.badRequest("Некорректный месяц начала рабочего года");
        if (settings.getWorkYearStartDay() < 1 || settings.getWorkYearStartDay() > 28)
            throw ApiException.badRequest("День начала рабочего года должен быть от 1 до 28");
        if (settings.getTimeOffBalanceMinutes() < 0 || settings.getTimeOffBalanceMinutes() > 600000)
            throw ApiException.badRequest("Баланс отгулов вне допустимого диапазона");
        if (settings.getDefaultTimeOffDayMinutes() < 15 || settings.getDefaultTimeOffDayMinutes() > 1440)
            throw ApiException.badRequest("Полный отгул должен быть от 15 минут до 24 часов");
    }

    private String normalizeCountMode(String value) {
        String mode = value == null ? "CALENDAR_DAYS" : value.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("CALENDAR_DAYS") && !mode.equals("WEEKDAYS"))
            throw ApiException.badRequest("countMode: CALENDAR_DAYS или WEEKDAYS");
        return mode;
    }

    private String normalizeStatus(String value) {
        String status = value == null || value.isBlank() ? "PLANNED" : value.trim().toUpperCase(Locale.ROOT);
        if (!status.equals("PLANNED") && !status.equals("APPROVED"))
            throw ApiException.badRequest("status: PLANNED или APPROVED");
        return status;
    }

    private String normalizeCoverage(String value) {
        String coverage = value == null || value.isBlank() ? FULL_DAY : value.trim().toUpperCase(Locale.ROOT);
        if (!FULL_DAY.equals(coverage) && !PARTIAL.equals(coverage))
            throw ApiException.badRequest("coverage: FULL_DAY или PARTIAL");
        return coverage;
    }

    private String normalizeBalancePolicy(String value, boolean legacyCounts) {
        String policy = value == null || value.isBlank() ? (legacyCounts ? VACATION_DAYS : NO_BALANCE)
                : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(VACATION_DAYS, TIME_OFF_HOURS, NO_BALANCE).contains(policy))
            throw ApiException.badRequest("balancePolicy: VACATION_DAYS, TIME_OFF_HOURS или NONE");
        return policy;
    }

    private void addDefaultType(AppUser user, String code, String name, String color,
                                String balancePolicy, boolean replacesShift, int order) {
        AbsenceType existing = typeRepository.findByOwnerAndSystemCode(user, code).orElse(null);
        if (existing == null) existing = typeRepository.findByOwnerAndNameIgnoreCase(user, name).orElse(null);
        boolean created = existing == null;
        if (created) existing = new AbsenceType(user);
        existing.setName(name);
        if (created || existing.getColor() == null || existing.getColor().isBlank()) existing.setColor(color);
        existing.setBalancePolicy(balancePolicy);
        existing.setCountsAgainstAllowance(VACATION_DAYS.equals(balancePolicy));
        existing.setFullDayReplacesShift(replacesShift);
        existing.setSystemPreset(true);
        existing.setSystemCode(code);
        existing.setSortOrder(order);
        typeRepository.save(existing);
    }

    private void ensureUniqueTypeName(AppUser user, String name, Long currentId) {
        typeRepository.findByOwnerAndNameIgnoreCase(user, name).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), currentId)) throw ApiException.conflict("Тип отсутствия с таким названием уже существует");
        });
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) throw ApiException.badRequest("Название типа отсутствия не должно быть пустым");
        return name;
    }

    private String normalizeColor(String value, String fallback) {
        String color = value == null || value.isBlank() ? fallback : value.trim();
        if (!color.matches("#[0-9a-fA-F]{6}")) throw ApiException.badRequest("Цвет должен быть в формате #RRGGBB");
        return color.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String displayTitle(AbsencePeriod period) {
        return period.getTitle() == null || period.getTitle().isBlank() ? period.getType().getName() : period.getTitle();
    }

    private int hoursToMinutes(double hours) { return Math.toIntExact(Math.round(hours * 60.0)); }
    private String timeString(LocalTime value) { return value == null ? null : value.toString(); }

    private record DateRange(LocalDate from, LocalDate to, int days) {}
    private record WorkYear(LocalDate start, LocalDate end) {}
    private record AllowanceProjection(WorkYear year, int available, int plannedBefore, int projected, int remaining) {}
    private record AbsenceShape(DateRange range, String coverage, LocalTime startTime, LocalTime endTime) {}
    private record ShiftPlan(String name, String color, int minutes) {}
    private static final class MutableTypeSummary {
        final Long typeId; final String typeName; final String typeColor; final String systemCode; final String balancePolicy;
        int fullDays; int partialMinutes; int chargedMinutes;
        MutableTypeSummary(Long typeId, String typeName, String typeColor, String systemCode, String balancePolicy) {
            this.typeId = typeId; this.typeName = typeName; this.typeColor = typeColor;
            this.systemCode = systemCode; this.balancePolicy = balancePolicy;
        }
    }
}
