package ru.daniil.shifts.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class ShiftTypeService {
    private static final String DEFAULT_COLOR = "#8B929E";

    private final ShiftTypeRepository shiftTypes;
    private final DayEntryRepository days;
    private final ShiftOccurrenceService shiftOccurrenceService;
    private final UserTimeService userTimeService;
    private final SecurityEventLogger securityEvents;

    public ShiftTypeService(ShiftTypeRepository shiftTypes,
                            DayEntryRepository days,
                            ShiftOccurrenceService shiftOccurrenceService,
                            UserTimeService userTimeService,
                            SecurityEventLogger securityEvents) {
        this.shiftTypes = shiftTypes;
        this.days = days;
        this.shiftOccurrenceService = shiftOccurrenceService;
        this.userTimeService = userTimeService;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public List<ShiftTypeDto> list(AppUser user) {
        ensureBuiltinShiftTypes(user);
        return shiftTypes.findByOwner(user).stream().map(ShiftTypeDto::from).toList();
    }

    @Transactional
    public ShiftTypeDto create(AppUser user, ShiftTypeCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }

        String name = cleanName(req.name());
        double hours = req.hours() != null ? req.hours() : 0;
        String color = req.color() != null ? req.color() : DEFAULT_COLOR;
        LocalTime startTime = parseOptionalTime(req.startTime());
        LocalTime endTime = parseOptionalTime(req.endTime());
        int breakMinutes = req.breakMinutes() != null ? req.breakMinutes() : 0;
        double plannedHours = req.plannedHours() != null ? req.plannedHours() : hours;

        ShiftType newType = new ShiftType(user, name, hours, color, false,
                startTime, endTime, breakMinutes, plannedHours);
        if (req.notificationsEnabled() != null) newType.setNotificationsEnabled(req.notificationsEnabled());
        if (req.notificationMinutesBefore() != null) newType.setNotificationMinutesBefore(req.notificationMinutesBefore() < 0 ? null : req.notificationMinutesBefore());
        ShiftType saved = shiftTypes.save(newType);
        return ShiftTypeDto.from(saved);
    }

    @Transactional
    public ShiftTypeDto update(AppUser user, Long id, ShiftTypeUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        ShiftType st = requireOwnedShiftType(user, id);

        // Встроенные смены можно настраивать по времени/обеду/плану,
        // но нельзя переименовывать или удалять. Так не ломаются шаблоны графика.
        if (!st.isBuiltin()) {
            if (req.name() != null) st.setName(cleanName(req.name()));
            if (req.color() != null) st.setColor(req.color());
        } else {
            if (req.name() != null || req.color() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "Название и цвет встроенной смены менять нельзя");
            }
        }

        if (req.hours() != null) st.setHours(req.hours());
        if (req.startTime() != null) st.setStartTime(parseOptionalTime(req.startTime()));
        if (req.endTime() != null) st.setEndTime(parseOptionalTime(req.endTime()));
        if (req.breakMinutes() != null) st.setBreakMinutes(req.breakMinutes());
        if (req.plannedHours() != null) st.setPlannedHours(req.plannedHours());
        if (req.notificationsEnabled() != null) st.setNotificationsEnabled(req.notificationsEnabled());
        if (req.notificationMinutesBefore() != null) st.setNotificationMinutesBefore(req.notificationMinutesBefore() < 0 ? null : req.notificationMinutesBefore());

        return ShiftTypeDto.from(shiftTypes.save(st));
    }

    /**
     * Rebase every timed shift template when the user changes the canonical
     * timezone. Existing dated occurrences are frozen before this method runs;
     * only future assignments use the projected wall-clock values.
     *
     * <p>The anchor date is the current date in the old zone. This preserves the
     * exact start/end instants and elapsed duration across ordinary offset and
     * DST changes while keeping the template editable as HH:mm values.</p>
     */
    @Transactional
    public int rebaseForTimezoneChange(AppUser user, String oldTimezone, String newTimezone) {
        ZoneId oldZone = userTimeService.resolveZone(oldTimezone, UserTimeService.FALLBACK_ZONE);
        ZoneId newZone = userTimeService.resolveZone(newTimezone, UserTimeService.FALLBACK_ZONE);
        if (oldZone.equals(newZone)) return 0;

        LocalDate anchorDate = userTimeService.nowInstant().atZone(oldZone).toLocalDate();
        int changed = 0;
        for (ShiftType shift : shiftTypes.findByOwner(user)) {
            LocalTime start = shift.getStartTime();
            LocalTime end = shift.getEndTime();
            if (start == null || end == null) continue;

            ZonedDateTime oldStart = userTimeService.resolveLocalDateTime(
                    LocalDateTime.of(anchorDate, start), oldZone);
            LocalDate endDate = end.isAfter(start) ? anchorDate : anchorDate.plusDays(1);
            ZonedDateTime oldEnd = userTimeService.resolveLocalDateTime(
                    LocalDateTime.of(endDate, end), oldZone);

            LocalTime projectedStart = oldStart.toInstant().atZone(newZone).toLocalTime()
                    .withSecond(0).withNano(0);
            LocalTime projectedEnd = oldEnd.toInstant().atZone(newZone).toLocalTime()
                    .withSecond(0).withNano(0);
            if (!projectedStart.equals(start) || !projectedEnd.equals(end)) {
                shift.setStartTime(projectedStart);
                shift.setEndTime(projectedEnd);
                changed++;
            }
        }
        if (changed > 0) shiftTypes.flush();
        return changed;
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ShiftType st = requireOwnedShiftType(user, id);
        if (st.isBuiltin()) {
            throw new ApiException(HttpStatus.CONFLICT, "Встроенную смену удалить нельзя");
        }

        days.findByShiftType(st).forEach(entry -> {
            shiftOccurrenceService.clear(entry);
            if (entry.isEmpty()) {
                days.delete(entry);
            } else {
                days.save(entry);
            }
        });
        shiftTypes.delete(st);
    }

    public ShiftType requireOwnedShiftType(AppUser user, Long id) {
        ShiftType type = shiftTypes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Смена не найдена"));
        if (!type.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=shift_type id=" + id);
            throw ApiException.notFound("Смена не найдена");
        }
        return type;
    }

    /**
     * Подстраховка для старых пользователей из предыдущих версий проекта:
     * если у них ещё нет встроенных смен, они появятся при следующей загрузке.
     */
    @Transactional
    public void ensureBuiltinShiftTypes(AppUser user) {
        ensureBuiltin(user, "Дневная", 8, "#F5B841", "08:30", "17:00", 30, 8.0);
        ensureBuiltin(user, "Ночная", 8, "#7B8CE0", "20:00", "08:00", 60, 11.0);
        ensureBuiltin(user, "Выходной", 0, "#6FBF73", null, null, 0, 0.0);
    }

    private void ensureBuiltin(AppUser user, String name, double hours, String color,
                               String startTime, String endTime, int breakMinutes, Double plannedHours) {
        List<ShiftType> existing = shiftTypes.findByOwnerAndName(user, name);
        if (existing.isEmpty()) {
            shiftTypes.save(new ShiftType(user, name, hours, color, true,
                    parseOptionalTime(startTime), parseOptionalTime(endTime), breakMinutes, plannedHours));
            return;
        }

        ShiftType first = existing.get(0);
        boolean changed = false;
        if (!first.isBuiltin()) {
            first.setBuiltin(true);
            changed = true;
        }
        // Для старых пользователей аккуратно заполняем новые поля только если они пустые.
        LocalTime desiredStart = parseOptionalTime(startTime);
        LocalTime desiredEnd = parseOptionalTime(endTime);
        if (first.getStartTime() == null && desiredStart != null) {
            first.setStartTime(desiredStart);
            changed = true;
        }
        // Мягкая миграция старого дефолта: дневная раньше была 06:30–17:00,
        // но для текущей логики пользователя удобнее 08:30–17:00. Если человек уже
        // менял время сам — не трогаем.
        if ("Дневная".equals(name)
                && first.getStartTime() != null
                && first.getStartTime().equals(LocalTime.parse("06:30"))
                && first.getEndTime() != null
                && first.getEndTime().equals(LocalTime.parse("17:00"))
                && desiredStart != null) {
            first.setStartTime(desiredStart);
            changed = true;
        }
        if (first.getEndTime() == null && desiredEnd != null) {
            first.setEndTime(desiredEnd);
            changed = true;
        }
        if (first.getBreakMinutes() == 0 && breakMinutes > 0) {
            first.setBreakMinutes(breakMinutes);
            changed = true;
        }
        if (first.getPlannedHours() == null) {
            first.setPlannedHours(plannedHours != null ? plannedHours : first.getHours());
            changed = true;
        }
        if (changed) shiftTypes.save(first);
    }

    private String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank()) throw ApiException.badRequest("Название смены не должно быть пустым");
        return name;
    }

    private LocalTime parseOptionalTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalTime.parse(raw.trim());
    }
}
