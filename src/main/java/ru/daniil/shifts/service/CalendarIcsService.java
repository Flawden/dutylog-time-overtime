package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.AbsenceOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.CalendarRangeDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ShiftOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** RFC 5545 writer for owner-scoped DutyLog calendar data. */
@Service
public class CalendarIcsService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
            .withZone(java.time.ZoneOffset.UTC);

    private final CalendarService calendarService;
    private final ImportantDayService importantDayService;
    private final DayEntryService dayEntryService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;
    private final int maxEvents;
    private final int maxBytes;

    public CalendarIcsService(
            CalendarService calendarService,
            ImportantDayService importantDayService,
            DayEntryService dayEntryService,
            UserTimeService userTimeService,
            SecurityEventLogger securityEvents,
            @Value("${dutylog.calendar-sync.max-events:10000}") int maxEvents,
            @Value("${dutylog.calendar-sync.max-bytes:5242880}") int maxBytes) {
        this.calendarService = calendarService;
        this.importantDayService = importantDayService;
        this.dayEntryService = dayEntryService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
        this.maxEvents = Math.max(1, maxEvents);
        this.maxBytes = Math.max(4096, maxBytes);
    }

    @Transactional
    public IcsExport exportRange(AppUser user, LocalDate from, LocalDate to) {
        IcsExport export = buildRange(user, from, to);
        securityEvents.info("DATA_EXPORT_CALENDAR", user.getUsername(), "accepted",
                "from=" + from + " to=" + to + " events=" + export.eventCount() + " bytes=" + export.bytes().length);
        return export;
    }

    /** Rolling feed rendering intentionally avoids one audit row per calendar-client poll. */
    @Transactional
    public IcsExport exportFeed(AppUser user, LocalDate from, LocalDate to) {
        return buildRange(user, from, to);
    }

    private IcsExport buildRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw ApiException.badRequest("Диапазон .ics-экспорта не должен превышать 366 календарных дней");
        }
        CalendarRangeDto range = calendarService.range(user, from, to);
        List<IcsEvent> events = rangeEvents(user, range);
        return render(user, from, to, events);
    }

    @Transactional(readOnly = true)
    public IcsExport exportImportantEvent(AppUser user, Long id) {
        if (id == null) throw ApiException.badRequest("Не указан id важного события");
        ImportantDayDto item = importantDayService.list(user).stream()
                .filter(candidate -> Objects.equals(candidate.id(), id))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Важное событие не найдено"));
        IcsEvent event = importantSourceEvent(user, item);
        LocalDate from = LocalDate.parse(item.date());
        LocalDate to = item.endDate() == null || item.endDate().isBlank() ? from : LocalDate.parse(item.endDate());
        IcsExport export = render(user, from, to, List.of(event));
        securityEvents.info("DATA_EXPORT_CALENDAR_EVENT", user.getUsername(), "accepted",
                "importantDayId=" + id + " bytes=" + export.bytes().length);
        return export;
    }

    List<IcsEvent> rangeEvents(AppUser user, CalendarRangeDto range) {
        List<IcsEvent> events = new ArrayList<>();
        Map<Long, String> shiftNames = new LinkedHashMap<>();
        for (ShiftTypeDto type : range.shiftTypes()) shiftNames.put(type.id(), type.name());

        for (ShiftOccurrenceDto shift : range.shiftOccurrences()) {
            Instant start = parseInstant(shift.startInstant());
            Instant end = parseInstant(shift.endInstant());
            if (start == null || end == null || !end.isAfter(start)) continue;
            String name = shiftNames.getOrDefault(shift.shiftTypeId(), "Смена");
            String details = "DutyLog shift";
            if (shift.sourceTimezone() != null) details += " · " + shift.sourceTimezone();
            if (shift.breakMinutes() > 0) details += " · break " + shift.breakMinutes() + " min";
            events.add(IcsEvent.timed(
                    "shift-" + shift.dayEntryId() + "@dutylog",
                    name,
                    details,
                    null,
                    "SHIFT",
                    start,
                    end,
                    false,
                    null
            ));
        }

        for (TaskDto task : range.tasks()) {
            IcsEvent event = taskEvent(user, task);
            if (event != null) events.add(event);
        }

        Map<String, ImportantDayOccurrenceDto> important = new LinkedHashMap<>();
        for (ImportantDayOccurrenceDto occurrence : range.importantDays()) {
            String key = occurrence.id() + "|" + occurrence.startDate() + "|" + Objects.toString(occurrence.startInstant(), "");
            important.putIfAbsent(key, occurrence);
        }
        for (ImportantDayOccurrenceDto occurrence : important.values()) {
            IcsEvent event = importantOccurrenceEvent(occurrence);
            if (event != null) events.add(event);
        }

        Map<Long, AbsenceOccurrenceDto> absences = new LinkedHashMap<>();
        for (AbsenceOccurrenceDto occurrence : range.absences()) {
            absences.putIfAbsent(occurrence.periodId(), occurrence);
        }
        for (AbsenceOccurrenceDto absence : absences.values()) {
            LocalDate start = parseDate(absence.startDate());
            LocalDate end = parseDate(absence.endDate());
            if (start == null || end == null || end.isBefore(start)) continue;
            String summary = blank(absence.title()) ? absence.typeName() : absence.title();
            String description = "DutyLog absence · " + Objects.toString(absence.status(), "PLANNED")
                    + (absence.plannedShiftName() == null ? "" : " · planned shift: " + absence.plannedShiftName());
            if ("PARTIAL".equals(absence.coverage())) {
                LocalTime startTime = parseTime(absence.startTime());
                LocalTime endTime = parseTime(absence.endTime());
                if (startTime == null || endTime == null || !endTime.isAfter(startTime)) continue;
                ZoneId zone = userTimeService.workZone(user);
                Instant startInstant = userTimeService.resolveLocalDateTime(LocalDateTime.of(start, startTime), zone).toInstant();
                Instant endInstant = userTimeService.resolveLocalDateTime(LocalDateTime.of(start, endTime), zone).toInstant();
                events.add(IcsEvent.timed("absence-" + absence.periodId() + "@dutylog", summary,
                        description, null, "ABSENCE,TIME-OFF", startInstant, endInstant, true, null));
            } else {
                events.add(IcsEvent.allDay(
                        "absence-" + absence.periodId() + "@dutylog",
                        summary,
                        description,
                        null,
                        "ABSENCE",
                        start,
                        end.plusDays(1),
                        true,
                        null
                ));
            }
        }

        events.sort(Comparator.comparing(IcsEvent::sortKey).thenComparing(IcsEvent::uid));
        return events;
    }

    private IcsEvent taskEvent(AppUser user, TaskDto task) {
        String uid = "task-" + task.id() + "@dutylog";
        String description = joinDetails(task.description(), prefixed("Project", task.project()), prefixed("Category", task.category()),
                task.dueDate() == null ? null : "Due: " + task.dueDate() + (task.dueTime() == null ? "" : " " + task.dueTime()),
                task.done() ? "Status: completed" : null);
        String categories = joinCategories("TASK", task.category(), task.project());
        LocalDate startDate = parseDate(firstNonBlank(task.scheduledStartDate(), task.date()));
        if (startDate == null) return null;

        if (task.allDay() || blank(task.scheduledStartTime())) {
            LocalDate inclusiveEnd = parseDate(task.scheduledEndDate());
            if (inclusiveEnd == null || inclusiveEnd.isBefore(startDate)) inclusiveEnd = startDate;
            return IcsEvent.allDay(uid, task.text(), description, null, categories,
                    startDate, inclusiveEnd.plusDays(1), false, null);
        }

        ZoneId zone = userTimeService.workZone(user);
        String sourceZone = task.scheduleAbsolute() ? task.scheduledSourceTimezone() : null;
        if (!blank(sourceZone)) zone = userTimeService.resolveZone(sourceZone, zone);
        LocalDate sourceStartDate = parseDate(task.scheduleAbsolute()
                ? firstNonBlank(task.scheduledSourceStartDate(), task.scheduledStartDate())
                : task.scheduledStartDate());
        LocalTime sourceStartTime = parseTime(task.scheduleAbsolute()
                ? firstNonBlank(task.scheduledSourceStartTime(), task.scheduledStartTime())
                : task.scheduledStartTime());
        if (sourceStartDate == null || sourceStartTime == null) return null;
        Instant start = userTimeService.resolveLocalDateTime(LocalDateTime.of(sourceStartDate, sourceStartTime), zone).toInstant();

        LocalDate sourceEndDate = parseDate(task.scheduleAbsolute()
                ? firstNonBlank(task.scheduledSourceEndDate(), task.scheduledEndDate())
                : task.scheduledEndDate());
        LocalTime sourceEndTime = parseTime(task.scheduleAbsolute()
                ? firstNonBlank(task.scheduledSourceEndTime(), task.scheduledEndTime())
                : task.scheduledEndTime());
        Instant end;
        if (sourceEndDate != null && sourceEndTime != null) {
            end = userTimeService.resolveLocalDateTime(LocalDateTime.of(sourceEndDate, sourceEndTime), zone).toInstant();
        } else if (task.scheduledDurationMinutes() != null && task.scheduledDurationMinutes() > 0) {
            end = start.plus(Duration.ofMinutes(task.scheduledDurationMinutes()));
        } else {
            end = start.plus(Duration.ofHours(1));
        }
        if (!end.isAfter(start)) return null;
        return IcsEvent.timed(uid, task.text(), description, null, categories, start, end, false, null);
    }

    private IcsEvent importantOccurrenceEvent(ImportantDayOccurrenceDto item) {
        String uid = "important-" + item.id() + "-" + firstNonBlank(item.startDate(), item.date()) + "@dutylog";
        String description = joinDetails(item.description(), prefixed("Type", String.valueOf(item.eventType())));
        String categories = joinCategories("IMPORTANT", item.category());
        if (item.allDay()) {
            LocalDate start = parseDate(firstNonBlank(item.startDate(), item.date()));
            LocalDate end = parseDate(firstNonBlank(item.endDate(), item.startDate(), item.date()));
            if (start == null || end == null || end.isBefore(start)) return null;
            return IcsEvent.allDay(uid, item.title(), description, item.place(), categories,
                    start, end.plusDays(1), false, null);
        }
        Instant start = parseInstant(item.startInstant());
        Instant end = parseInstant(item.endInstant());
        if (start == null || end == null || !end.isAfter(start)) return null;
        return IcsEvent.timed(uid, item.title(), description, item.place(), categories,
                start, end, false, null);
    }

    private IcsEvent importantSourceEvent(AppUser user, ImportantDayDto item) {
        String uid = "important-" + item.id() + "@dutylog";
        String description = joinDetails(item.description(), prefixed("Type", String.valueOf(item.eventType())));
        String categories = joinCategories("IMPORTANT", item.category());
        String recurrence = item.repeatMode() == RepeatMode.YEARLY ? "FREQ=YEARLY"
                : item.repeatMode() == RepeatMode.MONTHLY ? "FREQ=MONTHLY" : null;
        if (item.allDay()) {
            LocalDate start = LocalDate.parse(item.date());
            LocalDate end = blank(item.endDate()) ? start : LocalDate.parse(item.endDate());
            return IcsEvent.allDay(uid, item.title(), description, item.place(), categories,
                    start, end.plusDays(1), false, null).withRrule(recurrence);
        }
        Instant start = parseInstant(item.startInstant());
        Instant end = parseInstant(item.endInstant());
        if (start == null || end == null || !end.isAfter(start)) {
            ZoneId zone = userTimeService.resolveZone(item.sourceTimezone(), userTimeService.workZone(user));
            LocalDate startDate = LocalDate.parse(item.date());
            LocalDate endDate = blank(item.endDate()) ? startDate : LocalDate.parse(item.endDate());
            LocalTime startTime = LocalTime.parse(item.startTime());
            LocalTime endTime = LocalTime.parse(item.endTime());
            start = userTimeService.resolveLocalDateTime(LocalDateTime.of(startDate, startTime), zone).toInstant();
            end = userTimeService.resolveLocalDateTime(LocalDateTime.of(endDate, endTime), zone).toInstant();
        }
        return IcsEvent.timed(uid, item.title(), description, item.place(), categories,
                start, end, false, null).withRrule(recurrence);
    }

    private IcsExport render(AppUser user, LocalDate from, LocalDate to, List<IcsEvent> events) {
        if (events.size() > maxEvents) {
            throw ApiException.payloadTooLarge("Слишком много событий для одного .ics-экспорта: " + events.size());
        }
        Instant generatedAt = userTimeService.nowInstant();
        StringBuilder out = new StringBuilder(4096 + events.size() * 240);
        line(out, "BEGIN:VCALENDAR");
        line(out, "PRODID:-//DutyLog//Time and Overtime 27.26.2//RU");
        line(out, "VERSION:2.0");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escapeText("DutyLog — " + user.getUsername()));
        line(out, "X-WR-TIMEZONE:" + escapeText(userTimeService.workZone(user).getId()));
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT15M");
        line(out, "X-PUBLISHED-TTL:PT15M");
        for (IcsEvent event : events) appendEvent(out, event, generatedAt);
        line(out, "END:VCALENDAR");
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw ApiException.payloadTooLarge("Календарный экспорт превышает безопасный лимит " + maxBytes + " байт");
        }
        return new IcsExport(bytes, events.size(), from, to);
    }

    private void appendEvent(StringBuilder out, IcsEvent event, Instant generatedAt) {
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + event.uid());
        line(out, "DTSTAMP:" + UTC.format(generatedAt));
        if (event.allDay()) {
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(event.startDate()));
            line(out, "DTEND;VALUE=DATE:" + DATE.format(event.endDateExclusive()));
        } else {
            line(out, "DTSTART:" + UTC.format(event.startInstant()));
            line(out, "DTEND:" + UTC.format(event.endInstant()));
        }
        line(out, "SUMMARY:" + escapeText(event.summary()));
        if (!blank(event.description())) line(out, "DESCRIPTION:" + escapeText(event.description()));
        if (!blank(event.location())) line(out, "LOCATION:" + escapeText(event.location()));
        if (!blank(event.categories())) line(out, "CATEGORIES:" + event.categories());
        if (!blank(event.status())) line(out, "STATUS:" + event.status());
        if (!blank(event.rrule())) line(out, "RRULE:" + event.rrule());
        line(out, "TRANSP:" + (event.transparent() ? "TRANSPARENT" : "OPAQUE"));
        line(out, "END:VEVENT");
    }

    static String escapeText(String value) {
        return Objects.toString(value, "")
                .replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace(",", "\\,")
                .replace(";", "\\;");
    }

    /** Adds an RFC 5545 content line with CRLF and UTF-8-aware 75-octet folding. */
    static void line(StringBuilder out, String value) {
        String text = Objects.toString(value, "");
        StringBuilder segment = new StringBuilder();
        int bytes = 0;
        boolean continuation = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int charBytes = character.getBytes(StandardCharsets.UTF_8).length;
            int limit = continuation ? 74 : 75;
            if (bytes + charBytes > limit && segment.length() > 0) {
                out.append(segment).append("\r\n").append(' ');
                segment.setLength(0);
                bytes = 0;
                continuation = true;
            }
            segment.append(character);
            bytes += charBytes;
            offset += Character.charCount(codePoint);
        }
        out.append(segment).append("\r\n");
    }

    private static Instant parseInstant(String value) {
        try { return blank(value) ? null : Instant.parse(value); }
        catch (DateTimeException e) { return null; }
    }

    private static LocalDate parseDate(String value) {
        try { return blank(value) ? null : LocalDate.parse(value); }
        catch (DateTimeException e) { return null; }
    }

    private static LocalTime parseTime(String value) {
        try { return blank(value) ? null : LocalTime.parse(value); }
        catch (DateTimeException e) { return null; }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String prefixed(String prefix, String value) {
        return blank(value) ? null : prefix + ": " + value;
    }

    private static String joinDetails(String... values) {
        List<String> present = new ArrayList<>();
        if (values != null) for (String value : values) if (!blank(value)) present.add(value.trim());
        return String.join("\n", present);
    }

    private static String joinCategories(String... values) {
        List<String> present = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (!blank(value)) present.add(escapeText(value.trim()));
            }
        }
        return String.join(",", present);
    }

    public record IcsExport(byte[] bytes, int eventCount, LocalDate from, LocalDate to) {}

    record IcsEvent(
            String uid,
            String summary,
            String description,
            String location,
            String categories,
            boolean allDay,
            LocalDate startDate,
            LocalDate endDateExclusive,
            Instant startInstant,
            Instant endInstant,
            boolean transparent,
            String status,
            String rrule
    ) {
        static IcsEvent allDay(String uid, String summary, String description, String location, String categories,
                               LocalDate start, LocalDate endExclusive, boolean transparent, String status) {
            return new IcsEvent(uid, summary, description, location, categories, true,
                    start, endExclusive, null, null, transparent, status, null);
        }

        static IcsEvent timed(String uid, String summary, String description, String location, String categories,
                              Instant start, Instant end, boolean transparent, String status) {
            return new IcsEvent(uid, summary, description, location, categories, false,
                    null, null, start, end, transparent, status, null);
        }

        IcsEvent withRrule(String value) {
            return new IcsEvent(uid, summary, description, location, categories, allDay,
                    startDate, endDateExclusive, startInstant, endInstant, transparent, status, value);
        }

        String sortKey() {
            return allDay ? startDate.toString() : startInstant.toString();
        }
    }
}
