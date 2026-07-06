package ru.daniil.shifts.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.service.exception.ApiException;

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

        String name = req.name().trim();
        double hours = req.hours() != null ? req.hours() : 0;
        String color = req.color() != null ? req.color() : DEFAULT_COLOR;

        ShiftType saved = shiftTypes.save(new ShiftType(user, name, hours, color, false));
        return ShiftTypeDto.from(saved);
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
        ensureBuiltin(user, "Дневная", 8, "#F5B841");
        ensureBuiltin(user, "Ночная", 8, "#7B8CE0");
        ensureBuiltin(user, "Выходной", 0, "#6FBF73");
    }

    private void ensureBuiltin(AppUser user, String name, double hours, String color) {
        List<ShiftType> existing = shiftTypes.findByOwnerAndName(user, name);
        if (existing.isEmpty()) {
            shiftTypes.save(new ShiftType(user, name, hours, color, true));
            return;
        }

        ShiftType first = existing.get(0);
        if (!first.isBuiltin()) {
            first.setBuiltin(true);
            shiftTypes.save(first);
        }
    }
}
