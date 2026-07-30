package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.dto.Dtos.ImportantDayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantDay;
import ru.daniil.shifts.model.ImportantEventType;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.repo.ImportantDayRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

@Service
public class ImportantDayService {
    private static final String DEFAULT_COLOR = "#F5B841";
    private static final int MAX_REMINDERS = 10;
    private static final int MAX_REMINDER_MINUTES = 525_600;

    private final ImportantDayRepository importantDays;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;

    public ImportantDayService(ImportantDayRepository importantDays,
                               DayEntryService dayEntryService,
                               UserTimeService userTimeService,
                               SecurityEventLogger securityEvents) {
        this.importantDays = importantDays;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public List<ImportantDayDto> list(AppUser user) {
        return importantDays.findByOwnerOrderByDateAscIdAsc(user).stream()
                .map(ImportantDayDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ImportantDayOccurrenceDto> occurrences(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        List<ImportantDayOccurrenceDto> result = new ArrayList<>();
        for (ImportantDay day : importantDays.findByOwnerOrderByDateAscIdAsc(user)) {
            addOccurrences(user, day, from, to, result);
        }
        result.sort(Comparator
                .comparing(ImportantDayOccurrenceDto::date)
                .thenComparing(item -> item.allDay() ? "" : Objects.toString(item.startTime(), ""))
                .thenComparing(ImportantDayOccurrenceDto::title));
        return result;
    }

    @Transactional
    public ImportantDayDto create(AppUser user, ImportantDayCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate date = parseDate(req.date(), "Дата начала должна быть в формате yyyy-MM-dd");
        ImportantDay day = new ImportantDay(
                user,
                requireTitle(req.title()),
                date,
                req.repeatMode() == null ? RepeatMode.NONE : req.repeatMode(),
                normalizeColor(req.color())
        );
        day.setEventType(req.eventType());
        day.setEndDate(parseOptionalDate(req.endDate(), "Дата окончания должна быть в формате yyyy-MM-dd"));
        day.setAllDay(req.allDay() == null || req.allDay());
        day.setStartTime(parseOptionalTime(req.startTime(), "Время начала должно быть в формате HH:mm"));
        day.setEndTime(parseOptionalTime(req.endTime(), "Время окончания должно быть в формате HH:mm"));
        day.setSourceTimezone(normalizeOptional(req.sourceTimezone()));
        day.setPlace(normalizeOptional(req.place()));
        day.setDescription(normalizeOptional(req.description()));
        day.setIcon(normalizeOptional(req.icon()));
        day.setCategory(normalizeOptional(req.category()));
        day.setReminderOffsets(encodeReminders(req.reminders()));
        normalizeAndValidate(user, day);
        return ImportantDayDto.from(importantDays.save(day));
    }

    @Transactional
    public ImportantDayDto update(AppUser user, Long id, ImportantDayUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        ImportantDay day = requireOwnedImportantDay(user, id);

        if (req.title() != null) day.setTitle(requireTitle(req.title()));
        if (req.date() != null) day.setDate(parseDate(req.date(), "Дата начала должна быть в формате yyyy-MM-dd"));
        if (req.repeatMode() != null) day.setRepeatMode(req.repeatMode());
        if (req.color() != null) day.setColor(normalizeColor(req.color()));
        if (req.eventType() != null) day.setEventType(req.eventType());
        if (req.endDate() != null) day.setEndDate(parseOptionalDate(req.endDate(), "Дата окончания должна быть в формате yyyy-MM-dd"));
        if (req.allDay() != null) day.setAllDay(req.allDay());
        if (req.startTime() != null) day.setStartTime(parseOptionalTime(req.startTime(), "Время начала должно быть в формате HH:mm"));
        if (req.endTime() != null) day.setEndTime(parseOptionalTime(req.endTime(), "Время окончания должно быть в формате HH:mm"));
        if (req.sourceTimezone() != null) day.setSourceTimezone(normalizeOptional(req.sourceTimezone()));
        if (req.place() != null) day.setPlace(normalizeOptional(req.place()));
        if (req.description() != null) day.setDescription(normalizeOptional(req.description()));
        if (req.icon() != null) day.setIcon(normalizeOptional(req.icon()));
        if (req.category() != null) day.setCategory(normalizeOptional(req.category()));
        if (req.reminders() != null) day.setReminderOffsets(encodeReminders(req.reminders()));

        normalizeAndValidate(user, day);
        return ImportantDayDto.from(importantDays.save(day));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ImportantDay day = requireOwnedImportantDay(user, id);
        importantDays.delete(day);
    }

    private ImportantDay requireOwnedImportantDay(AppUser user, Long id) {
        if (id == null) throw ApiException.badRequest("Не указан id важного события");
        ImportantDay day = importantDays.findById(id)
                .orElseThrow(() -> ApiException.notFound("Важное событие не найдено"));
        if (!Objects.equals(day.getOwner().getId(), user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=important_day id=" + id);
            throw ApiException.notFound("Важное событие не найдено");
        }
        return day;
    }

    private void normalizeAndValidate(AppUser user, ImportantDay day) {
        if (day.getDate() == null) throw ApiException.badRequest("Дата начала обязательна");
        day.setTitle(requireTitle(day.getTitle()));
        day.setColor(normalizeColor(day.getColor()));
        day.setPlace(normalizeOptional(day.getPlace()));
        day.setDescription(normalizeOptional(day.getDescription()));
        day.setIcon(normalizeOptional(day.getIcon()));
        day.setCategory(normalizeOptional(day.getCategory()));

        ImportantEventType type = day.getEventType();
        if (type == ImportantEventType.IMPORTANT_DATE) {
            day.setAllDay(true);
            day.setEndDate(null);
            clearTimedFields(day);
            return;
        }

        if (type == ImportantEventType.EVENT && day.getEndDate() == null) {
            day.setEndDate(day.getDate());
        }
        if (type == ImportantEventType.PERIOD && day.getEndDate() == null) {
            throw ApiException.badRequest("Для периода нужна дата окончания");
        }
        if (day.getEndDate() != null && day.getEndDate().isBefore(day.getDate())) {
            throw ApiException.badRequest("Окончание важного события не может быть раньше начала");
        }

        if (day.isAllDay()) {
            clearTimedFields(day);
            return;
        }

        if (day.getStartTime() == null || day.getEndTime() == null) {
            throw ApiException.badRequest("Для события со временем нужны начало и окончание");
        }
        if (day.getEndDate() == null) day.setEndDate(day.getDate());

        ZoneId sourceZone = parseZone(day.getSourceTimezone(), userTimeService.workZone(user));
        LocalDateTime sourceStart = LocalDateTime.of(day.getDate(), day.getStartTime());
        LocalDateTime sourceEnd = LocalDateTime.of(day.getEndDate(), day.getEndTime());
        ZonedDateTime resolvedStart = userTimeService.resolveLocalDateTime(sourceStart, sourceZone);
        ZonedDateTime resolvedEnd = userTimeService.resolveLocalDateTime(sourceEnd, sourceZone);
        if (!resolvedEnd.toInstant().isAfter(resolvedStart.toInstant())) {
            throw ApiException.badRequest("Окончание важного события должно быть позже начала");
        }
        day.setSourceTimezone(sourceZone.getId());
        day.setStartInstant(resolvedStart.toInstant());
        day.setEndInstant(resolvedEnd.toInstant());
    }

    private void clearTimedFields(ImportantDay day) {
        day.setStartTime(null);
        day.setEndTime(null);
        day.setStartInstant(null);
        day.setEndInstant(null);
        day.setSourceTimezone(null);
    }

    private void addOccurrences(AppUser user,
                                ImportantDay day,
                                LocalDate from,
                                LocalDate to,
                                List<ImportantDayOccurrenceDto> out) {
        for (LocalDate occurrenceStart : recurrenceStarts(day, from, to)) {
            if (day.isAllDay()) addAllDayOccurrence(user, day, occurrenceStart, from, to, out);
            else addTimedOccurrence(user, day, occurrenceStart, from, to, out);
        }
    }

    private void addAllDayOccurrence(AppUser user,
                                     ImportantDay day,
                                     LocalDate occurrenceStart,
                                     LocalDate from,
                                     LocalDate to,
                                     List<ImportantDayOccurrenceDto> out) {
        long durationDays = day.getEventType() == ImportantEventType.IMPORTANT_DATE || day.getEndDate() == null
                ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(day.getDate(), day.getEndDate()));
        LocalDate occurrenceEnd = occurrenceStart.plusDays(durationDays);
        if (occurrenceEnd.isBefore(from) || occurrenceStart.isAfter(to)) return;
        LocalDate visibleStart = occurrenceStart.isBefore(from) ? from : occurrenceStart;
        LocalDate visibleEnd = occurrenceEnd.isAfter(to) ? to : occurrenceEnd;
        for (LocalDate cursor = visibleStart; !cursor.isAfter(visibleEnd); cursor = cursor.plusDays(1)) {
            out.add(toOccurrence(user, day, cursor, occurrenceStart, occurrenceEnd,
                    true, null, null, null, null));
        }
    }

    private void addTimedOccurrence(AppUser user,
                                    ImportantDay day,
                                    LocalDate occurrenceStart,
                                    LocalDate from,
                                    LocalDate to,
                                    List<ImportantDayOccurrenceDto> out) {
        long sourceDayOffset = Math.max(0L, ChronoUnit.DAYS.between(day.getDate(), day.getEndDate()));
        LocalDate occurrenceEndDate = occurrenceStart.plusDays(sourceDayOffset);
        ZoneId sourceZone = parseZone(day.getSourceTimezone(), userTimeService.workZone(user));
        Instant startInstant = userTimeService.resolveLocalDateTime(
                LocalDateTime.of(occurrenceStart, day.getStartTime()), sourceZone).toInstant();
        Instant endInstant = userTimeService.resolveLocalDateTime(
                LocalDateTime.of(occurrenceEndDate, day.getEndTime()), sourceZone).toInstant();
        if (!endInstant.isAfter(startInstant)) return;

        ZoneId displayZone = userTimeService.displayZone(user);
        ZonedDateTime displayStart = startInstant.atZone(displayZone);
        ZonedDateTime displayEnd = endInstant.atZone(displayZone);
        LocalDate displayStartDate = displayStart.toLocalDate();
        LocalDate displayEndDate = endInstant.minusNanos(1).atZone(displayZone).toLocalDate();
        if (displayEndDate.isBefore(from) || displayStartDate.isAfter(to)) return;

        LocalDate visibleStart = displayStartDate.isBefore(from) ? from : displayStartDate;
        LocalDate visibleEnd = displayEndDate.isAfter(to) ? to : displayEndDate;
        String startTime = minuteString(displayStart.toLocalTime());
        String endTime = minuteString(displayEnd.toLocalTime());
        for (LocalDate cursor = visibleStart; !cursor.isAfter(visibleEnd); cursor = cursor.plusDays(1)) {
            out.add(toOccurrence(user, day, cursor, displayStartDate, displayEndDate,
                    false, startTime, endTime, startInstant, endInstant));
        }
    }

    private ImportantDayOccurrenceDto toOccurrence(AppUser user,
                                                    ImportantDay day,
                                                    LocalDate bucketDate,
                                                    LocalDate startDate,
                                                    LocalDate endDate,
                                                    boolean allDay,
                                                    String startTime,
                                                    String endTime,
                                                    Instant startInstant,
                                                    Instant endInstant) {
        return new ImportantDayOccurrenceDto(
                day.getId(),
                bucketDate.toString(),
                day.getTitle(),
                day.getRepeatMode(),
                day.getColor(),
                day.getEventType(),
                startDate.toString(),
                endDate.toString(),
                allDay,
                startTime,
                endTime,
                startInstant == null ? null : startInstant.toString(),
                endInstant == null ? null : endInstant.toString(),
                day.getSourceTimezone(),
                userTimeService.displayZone(user).getId(),
                day.getPlace(),
                day.getDescription(),
                day.getIcon(),
                day.getCategory(),
                decodeReminders(day.getReminderOffsets())
        );
    }

    private List<LocalDate> recurrenceStarts(ImportantDay day, LocalDate from, LocalDate to) {
        RepeatMode mode = day.getRepeatMode();
        if (mode == RepeatMode.NONE) return List.of(day.getDate());
        List<LocalDate> starts = new ArrayList<>();
        long durationDays = day.getEndDate() == null ? 0L
                : Math.max(0L, ChronoUnit.DAYS.between(day.getDate(), day.getEndDate()));
        long bufferDays = Math.min(366L, durationDays + 2L);
        LocalDate bufferedFrom = from.minusDays(bufferDays);
        LocalDate bufferedTo = to.plusDays(bufferDays);
        if (mode == RepeatMode.YEARLY) {
            for (int year = bufferedFrom.getYear() - 1; year <= bufferedTo.getYear() + 1; year++) {
                starts.add(yearlyOccurrence(day.getDate(), year));
            }
        } else if (mode == RepeatMode.MONTHLY) {
            YearMonth month = YearMonth.from(bufferedFrom).minusMonths(1);
            YearMonth end = YearMonth.from(bufferedTo).plusMonths(1);
            while (!month.isAfter(end)) {
                starts.add(monthlyOccurrence(day.getDate(), month));
                month = month.plusMonths(1);
            }
        }
        return starts.stream().distinct().toList();
    }

    /** 29 February falls back to 28 February in non-leap years. */
    private LocalDate yearlyOccurrence(LocalDate base, int year) {
        try {
            return LocalDate.of(year, base.getMonth(), base.getDayOfMonth());
        } catch (DateTimeException ex) {
            return LocalDate.of(year, 2, 28);
        }
    }

    /** Monthly events on the 29th-31st clamp to the last day of a short month. */
    private LocalDate monthlyOccurrence(LocalDate base, YearMonth month) {
        return month.atDay(Math.min(base.getDayOfMonth(), month.lengthOfMonth()));
    }

    private LocalDate parseDate(String value, String message) {
        return dayEntryService.parseDate(value, message);
    }

    private LocalDate parseOptionalDate(String value, String message) {
        if (value == null || value.isBlank()) return null;
        return parseDate(value, message);
    }

    private LocalTime parseOptionalTime(String value, String message) {
        if (value == null || value.isBlank()) return null;
        try { return LocalTime.parse(value.trim()); }
        catch (DateTimeException ex) { throw ApiException.badRequest(message); }
    }

    private ZoneId parseZone(String value, ZoneId fallback) {
        String candidate = value == null || value.isBlank() ? fallback.getId() : value.trim();
        try { return ZoneId.of(candidate); }
        catch (DateTimeException ex) { throw ApiException.badRequest("Неизвестный IANA-часовой пояс: " + candidate); }
    }

    private String requireTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isBlank()) throw ApiException.badRequest("Название важного события не должно быть пустым");
        return title;
    }

    private String normalizeColor(String value) {
        return value == null || value.isBlank() ? DEFAULT_COLOR : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String encodeReminders(List<Integer> values) {
        if (values == null || values.isEmpty()) return null;
        TreeSet<Integer> normalized = new TreeSet<>();
        for (Integer value : values) {
            if (value == null || value < 0 || value > MAX_REMINDER_MINUTES) {
                throw ApiException.badRequest("Некорректное напоминание важного события");
            }
            normalized.add(value);
        }
        if (normalized.size() > MAX_REMINDERS) {
            throw ApiException.badRequest("Можно задать максимум " + MAX_REMINDERS + " напоминаний");
        }
        return normalized.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private List<Integer> decodeReminders(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Integer> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            try { values.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) { /* skip legacy/corrupt token */ }
        }
        return values.stream().distinct().sorted().toList();
    }

    private String minuteString(LocalTime value) {
        return value == null ? null : String.format("%02d:%02d", value.getHour(), value.getMinute());
    }
}
