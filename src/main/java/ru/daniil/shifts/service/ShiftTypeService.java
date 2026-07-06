package ru.daniil.shifts.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.dto.Dtos.ShiftTypeUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalTime;
import java.util.List;

@Service
public class ShiftTypeService {
    private static final String DEFAULT_COLOR = "#8B929E";

    private final ShiftTypeRepository shiftTypes;
    private final DayEntryRepository days;

    public ShiftTypeService(ShiftTypeRepository shiftTypes, DayEntryRepository days) {
        this.shiftTypes = shiftTypes;
        this.days = days;
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

    @Transactional
    public void delete(AppUser user, Long id) {
        ShiftType st = requireOwnedShiftType(user, id);
        if (st.isBuiltin()) {
            throw new ApiException(HttpStatus.CONFLICT, "Встроенную смену удалить нельзя");
        }

        days.findByShiftType(st).forEach(entry -> {
            entry.setShiftType(null);
            if (entry.isEmpty()) {
                days.delete(entry);
            } else {
                days.save(entry);
            }
        });
        shiftTypes.delete(st);
    }

    public ShiftType requireOwnedShiftType(AppUser user, Long id) {
        return shiftTypes.findById(id)
                .filter(type -> type.getOwner().getId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Смена не найдена"));
    }

    /**
     * Подстраховка для старых пользователей из предыдущих версий проекта:
     * если у них ещё нет встроенных смен, они появятся при следующей загрузке.
     */
    @Transactional
    public void ensureBuiltinShiftTypes(AppUser user) {
        ensureBuiltin(user, "Дневная", 8, "#F5B841", "06:30", "17:00", 30, 8.0);
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
        if (first.getStartTime() == null && startTime != null) {
            first.setStartTime(parseOptionalTime(startTime));
            changed = true;
        }
        if (first.getEndTime() == null && endTime != null) {
            first.setEndTime(parseOptionalTime(endTime));
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
