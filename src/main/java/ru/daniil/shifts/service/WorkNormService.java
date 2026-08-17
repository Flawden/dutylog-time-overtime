package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

/** Canonical conversion of a dated schedule entry into base work-norm minutes. */
@Service
public class WorkNormService {
    public int basePlannedMinutes(DayEntry entry) {
        if (entry == null || entry.getShiftType() == null) return 0;
        if (entry.getShiftNetMinutes() > 0) return Math.toIntExact(entry.getShiftNetMinutes());
        ShiftType shift = entry.getShiftType();
        return Math.max(0, (int) Math.round(shift.effectivePlannedHours() * 60.0));
    }
}
