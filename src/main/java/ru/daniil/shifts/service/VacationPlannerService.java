package ru.daniil.shifts.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Vacation and absence planning without polluting shift rows.
 *
 * <p>The model is deliberately country-neutral: users choose their work-year
 * boundary and whether allowance counts all civil days or Monday-Friday.
 * Public-holiday legislation is not hardcoded.</p>
 */
@Service
public class VacationPlannerService {
    public static final int MAX_PERIOD_DAYS = 400;
    public static final List<Integer> DURATION_PRESETS = List.of(14, 28, 35);

    private final VacationSettingsRepository settingsRepository;
    private final AbsenceTypeRepository typeRepository;
    private final AbsencePeriodRepository periodRepository;
    private final DayEntryRepository dayRepository;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;

    public VacationPlannerService(VacationSettingsRepository settingsRepository,
                                  AbsenceTypeRepository typeRepository,
                                  AbsencePeriodRepository periodRepository,
                                  DayEntryRepository dayRepository,
                                  DayEntryService dayEntryService,
                                  UserTimeService userTimeService,
                                  SecurityEventLogger securityEvents) {
        this.settingsRepository = settingsRepository;
        this.typeRepository = typeRepository;
        this.periodRepository = periodRepository;
        this.dayRepository = dayRepository;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
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
        return new VacationPlannerDto(
                VacationSettingsDto.from(settings),
                summary(user, settings, reference, null),
                DURATION_PRESETS,
                typeRepository.findByOwnerOrderBySortOrderAscIdAsc(user).stream().map(AbsenceTypeDto::from).toList(),
                periods.stream().map(period -> toPeriodDto(settings, period)).toList(),
                occurrences(user, settings, rangeFrom, rangeTo)
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
        validateSettings(settings);

        validateAllStoredWorkYears(user, settings);
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
        type.setCountsAgainstAllowance(Boolean.TRUE.equals(req.countsAgainstAllowance()));
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
        if (type.isSystemPreset() && (req.name() != null || req.countsAgainstAllowance() != null)) {
            throw ApiException.conflict("Встроенный тип можно перекрасить и переместить, но нельзя менять его смысл");
        }
        if (req.name() != null) {
            String name = cleanName(req.name());
            ensureUniqueTypeName(user, name, type.getId());
            type.setName(name);
        }
        if (req.color() != null) type.setColor(normalizeColor(req.color(), type.getColor()));
        if (req.countsAgainstAllowance() != null
                && req.countsAgainstAllowance() != type.isCountsAgainstAllowance()) {
            if (periodRepository.existsByType(type)) {
                throw ApiException.conflict("ABSENCE_TYPE_IN_USE",
                        "Нельзя менять списание нормы у типа, который уже используется в периодах");
            }
            type.setCountsAgainstAllowance(req.countsAgainstAllowance());
        }
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
        DateRange range = parseRange(req.startDate(), req.endDate());
        return buildPreview(user, settings, type, range, req.excludePeriodId());
    }

    @Transactional
    public AbsencePeriodDto createPeriod(AppUser user, AbsencePeriodCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = lockSettings(user);
        ensureDefaultTypes(user);
        AbsenceType type = requireOwnedType(user, req.typeId());
        DateRange range = parseRange(req.startDate(), req.endDate());
        validateNoOverlap(user, range, null);
        validateAllowanceAcrossWorkYears(user, settings, type, range, null);

        AbsencePeriod period = new AbsencePeriod(user);
        period.setType(type);
        period.setTitle(normalizeOptional(req.title()));
        period.setStartDate(range.from());
        period.setEndDate(range.to());
        period.setStatus(normalizeStatus(req.status()));
        period.setNote(normalizeOptional(req.note()));
        return toPeriodDto(settings, periodRepository.saveAndFlush(period));
    }

    @Transactional
    public AbsencePeriodDto updatePeriod(AppUser user, Long id, AbsencePeriodUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        VacationSettings settings = lockSettings(user);
        AbsencePeriod period = requireOwnedPeriod(user, id);
        AbsenceType type = req.typeId() == null ? period.getType() : requireOwnedType(user, req.typeId());
        LocalDate from = req.startDate() == null ? period.getStartDate() : parseDate(req.startDate(), "Дата начала должна быть в формате yyyy-MM-dd");
        LocalDate to = req.endDate() == null ? period.getEndDate() : parseDate(req.endDate(), "Дата окончания должна быть в формате yyyy-MM-dd");
        DateRange range = validateRange(from, to);
        validateNoOverlap(user, range, id);
        validateAllowanceAcrossWorkYears(user, settings, type, range, id);

        period.setType(type);
        period.setStartDate(from);
        period.setEndDate(to);
        if (Boolean.TRUE.equals(req.clearTitle())) period.setTitle(null);
        else if (req.title() != null) period.setTitle(normalizeOptional(req.title()));
        if (Boolean.TRUE.equals(req.clearNote())) period.setNote(null);
        else if (req.note() != null) period.setNote(normalizeOptional(req.note()));
        if (req.status() != null) period.setStatus(normalizeStatus(req.status()));
        return toPeriodDto(settings, periodRepository.saveAndFlush(period));
    }

    @Transactional
    public void deletePeriod(AppUser user, Long id) {
        periodRepository.delete(requireOwnedPeriod(user, id));
    }

    @Transactional
    public List<AbsenceOccurrenceDto> occurrences(AppUser user, LocalDate from, LocalDate to) {
        return occurrences(user, ensureSettings(user), from, to);
    }

    private List<AbsenceOccurrenceDto> occurrences(AppUser user,
                                                    VacationSettings settings,
                                                    LocalDate from,
                                                    LocalDate to) {
        List<AbsencePeriod> periods = periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, from, to);
        Set<LocalDate> shifts = shiftDates(user, from, to);
        List<AbsenceOccurrenceDto> out = new ArrayList<>();
        for (AbsencePeriod period : periods) {
            LocalDate visibleFrom = period.getStartDate().isBefore(from) ? from : period.getStartDate();
            LocalDate visibleTo = period.getEndDate().isAfter(to) ? to : period.getEndDate();
            for (LocalDate date = visibleFrom; !date.isAfter(visibleTo); date = date.plusDays(1)) {
                AbsenceType type = period.getType();
                out.add(new AbsenceOccurrenceDto(
                        period.getId(), type.getId(), type.getName(), type.getColor(), type.getSystemCode(),
                        period.getTitle(), date.toString(), period.getStartDate().toString(), period.getEndDate().toString(),
                        period.getStatus(), type.isCountsAgainstAllowance() && countedByMode(settings, date), shifts.contains(date)
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
        addDefaultType(user, "VACATION", "Отпуск", "#4FA3A5", true, 10);
        addDefaultType(user, "SICK", "Больничный", "#E0653A", false, 20);
        addDefaultType(user, "UNPAID", "Без содержания", "#8B929E", false, 30);
        addDefaultType(user, "OTHER", "Другое", "#9B7BE0", false, 40);
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

    private AbsencePreviewDto buildPreview(AppUser user,
                                            VacationSettings settings,
                                            AbsenceType type,
                                            DateRange range,
                                            Long excludePeriodId) {
        List<AbsencePeriod> overlaps = periodRepository
                .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(user, range.from(), range.to())
                .stream().filter(period -> !Objects.equals(period.getId(), excludePeriodId)).toList();
        Map<LocalDate, AbsencePeriod> absenceByDate = new HashMap<>();
        for (AbsencePeriod overlap : overlaps) {
            LocalDate start = overlap.getStartDate().isBefore(range.from()) ? range.from() : overlap.getStartDate();
            LocalDate end = overlap.getEndDate().isAfter(range.to()) ? range.to() : overlap.getEndDate();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) absenceByDate.putIfAbsent(date, overlap);
        }
        Set<LocalDate> shifts = shiftDates(user, range.from(), range.to());
        List<AbsencePreviewItemDto> items = new ArrayList<>();
        int counted = 0;
        int shiftConflicts = 0;
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            boolean weekend = isWeekend(date);
            boolean countedDay = type.isCountsAgainstAllowance() && countedByMode(settings, date);
            boolean shiftConflict = shifts.contains(date);
            AbsencePeriod existing = absenceByDate.get(date);
            if (countedDay) counted++;
            if (shiftConflict) shiftConflicts++;
            items.add(new AbsencePreviewItemDto(
                    date.toString(), weekend, countedDay, shiftConflict,
                    existing == null ? null : existing.getId(),
                    existing == null ? null : displayTitle(existing),
                    existing == null ? "APPLY" : "CONFLICT"
            ));
        }

        AllowanceProjection critical = mostConstrainedProjection(
                user, settings, type, range, excludePeriodId);
        boolean exceedsAllowance = type.isCountsAgainstAllowance() && critical.remaining() < 0;
        return new AbsencePreviewDto(
                type.getId(), type.getName(), range.from().toString(), range.to().toString(), range.days(), counted,
                shiftConflicts, absenceByDate.size(), critical.year().start().toString(), critical.year().end().toString(),
                critical.available(), critical.plannedBefore(), critical.projected(), critical.remaining(),
                exceedsAllowance, exceedsAllowance ? Math.max(0, -critical.remaining()) : 0, items
        );
    }

    private VacationSummaryDto summary(AppUser user, VacationSettings settings, LocalDate reference, Long excludePeriodId) {
        WorkYear year = workYearContaining(settings, reference);
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        int planned = plannedDays(user, settings, year, excludePeriodId);
        return new VacationSummaryDto(year.start().toString(), year.end().toString(),
                settings.getAnnualAllowanceDays(), settings.getCarryoverDays(), available, planned,
                available - planned, settings.getCountMode());
    }

    /**
     * A preview may cross a work-year boundary. Report the intersected year with
     * the smallest remaining balance so the UI never hides an overflow that the
     * authoritative create/update operation would reject.
     */
    private AllowanceProjection mostConstrainedProjection(AppUser user,
                                                          VacationSettings settings,
                                                          AbsenceType type,
                                                          DateRange requested,
                                                          Long excludePeriodId) {
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        WorkYear year = workYearContaining(settings, requested.from());
        AllowanceProjection critical = null;
        while (!year.start().isAfter(requested.to())) {
            int plannedBefore = plannedDays(user, settings, year, excludePeriodId);
            int added = type.isCountsAgainstAllowance()
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
            if (Objects.equals(period.getId(), excludePeriodId) || !period.getType().isCountsAgainstAllowance()) continue;
            total += countIntersection(settings, period.getStartDate(), period.getEndDate(), year.start(), year.end());
        }
        return total;
    }

    private void validateAllStoredWorkYears(AppUser user, VacationSettings settings) {
        int available = settings.getAnnualAllowanceDays() + settings.getCarryoverDays();
        Set<WorkYear> years = new LinkedHashSet<>();
        for (AbsencePeriod period : periodRepository.findByOwnerOrderByStartDateAscIdAsc(user)) {
            if (!period.getType().isCountsAgainstAllowance()) continue;
            WorkYear year = workYearContaining(settings, period.getStartDate());
            while (!year.start().isAfter(period.getEndDate())) {
                years.add(year);
                year = new WorkYear(year.start().plusYears(1), year.end().plusYears(1));
            }
        }
        for (WorkYear year : years) {
            int planned = plannedDays(user, settings, year, null);
            if (planned > available) {
                throw ApiException.conflict("VACATION_LIMIT_EXCEEDED",
                        "Новые правила уменьшают доступный отпуск ниже уже запланированного количества дней в рабочем году "
                                + year.start() + " — " + year.end());
            }
        }
    }

    private void validateAllowanceAcrossWorkYears(AppUser user,
                                                  VacationSettings settings,
                                                  AbsenceType type,
                                                  DateRange requested,
                                                  Long excludePeriodId) {
        if (!type.isCountsAgainstAllowance()) return;
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

    private void validateNoOverlap(AppUser user, DateRange range, Long excludePeriodId) {
        if (periodRepository.countOverlapping(user, range.from(), range.to(), excludePeriodId) > 0) {
            throw ApiException.conflict("ABSENCE_OVERLAP", "Период пересекается с другим отсутствием");
        }
    }

    private AbsencePeriodDto toPeriodDto(VacationSettings settings, AbsencePeriod period) {
        int calendarDays = (int) ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        int countedDays = period.getType().isCountsAgainstAllowance()
                ? countDays(settings, period.getStartDate(), period.getEndDate()) : 0;
        int shiftConflicts = shiftDates(period.getOwner(), period.getStartDate(), period.getEndDate()).size();
        AbsenceType type = period.getType();
        return new AbsencePeriodDto(
                period.getId(), type.getId(), type.getName(), type.getColor(), type.getSystemCode(),
                type.isCountsAgainstAllowance(), period.getTitle(), period.getStartDate().toString(), period.getEndDate().toString(),
                period.getStatus(), period.getNote(), calendarDays, countedDays, shiftConflicts,
                period.getCreatedAt() == null ? null : period.getCreatedAt().toString(),
                period.getUpdatedAt() == null ? null : period.getUpdatedAt().toString()
        );
    }

    private Set<LocalDate> shiftDates(AppUser user, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (DayEntry entry : dayRepository.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)) {
            if (entry.getShiftType() != null) dates.add(entry.getDate());
        }
        return dates;
    }

    private int countIntersection(VacationSettings settings,
                                  LocalDate firstStart,
                                  LocalDate firstEnd,
                                  LocalDate secondStart,
                                  LocalDate secondEnd) {
        LocalDate start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        LocalDate end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return start.isAfter(end) ? 0 : countDays(settings, start, end);
    }

    private int countDays(VacationSettings settings, LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (countedByMode(settings, date)) count++;
        }
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

    private DateRange parseRange(String from, String to) {
        return validateRange(
                parseDate(from, "Дата начала должна быть в формате yyyy-MM-dd"),
                parseDate(to, "Дата окончания должна быть в формате yyyy-MM-dd")
        );
    }

    private DateRange validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw ApiException.badRequest("Дата окончания не может быть раньше даты начала");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_PERIOD_DAYS) throw ApiException.badRequest("Период отсутствия: максимум " + MAX_PERIOD_DAYS + " дней");
        return new DateRange(from, to, (int) days);
    }

    private LocalDate parseDate(String value, String message) {
        return dayEntryService.parseDate(value, message);
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

    private void addDefaultType(AppUser user, String code, String name, String color, boolean counts, int order) {
        AbsenceType existing = typeRepository.findByOwnerAndSystemCode(user, code).orElse(null);
        if (existing != null) return;
        existing = typeRepository.findByOwnerAndNameIgnoreCase(user, name).orElse(null);
        boolean created = existing == null;
        if (created) existing = new AbsenceType(user);
        existing.setName(name);
        if (created || existing.getColor() == null || existing.getColor().isBlank()) existing.setColor(color);
        existing.setCountsAgainstAllowance(counts);
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

    private record DateRange(LocalDate from, LocalDate to, int days) {}
    private record WorkYear(LocalDate start, LocalDate end) {}
    private record AllowanceProjection(WorkYear year, int available, int plannedBefore, int projected, int remaining) {}
}
