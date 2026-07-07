package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;

import java.time.LocalTime;
import java.util.List;

/**
 * Минимальный стартовый набор смен для нового пользователя.
 */
@Service
public class DefaultShiftSeedService {

    private final ShiftTypeRepository shiftTypes;

    public DefaultShiftSeedService(ShiftTypeRepository shiftTypes) {
        this.shiftTypes = shiftTypes;
    }

    @Transactional
    public void seedDefaults(AppUser user) {
        shiftTypes.saveAll(List.of(
                new ShiftType(user, "Дневная", 8, "#F5B841", true, LocalTime.parse("08:30"), LocalTime.parse("17:00"), 30, 8.0),
                new ShiftType(user, "Ночная", 8, "#7B8CE0", true, LocalTime.parse("20:00"), LocalTime.parse("08:00"), 60, 11.0),
                new ShiftType(user, "Выходной", 0, "#6FBF73", true, null, null, 0, 0.0)
        ));
    }
}
