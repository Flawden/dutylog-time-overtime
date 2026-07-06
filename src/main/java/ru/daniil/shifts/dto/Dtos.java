package ru.daniil.shifts.dto;

import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

/**
 * DTO-шки API. Вложенные records — легальный способ держать
 * несколько мелких типов в одном файле.
 */
public final class Dtos {
    private Dtos() {}

    /** Тип смены наружу. */
    public record ShiftTypeDto(Long id, String name, double hours, String color, boolean builtin) {
        public static ShiftTypeDto from(ShiftType s) {
            return new ShiftTypeDto(s.getId(), s.getName(), s.getHours(), s.getColor(), s.isBuiltin());
        }
    }

    /** Создание нового типа смены. */
    public record ShiftTypeCreateRequest(String name, Double hours, String color) {}

    /** Запись дня наружу: дата в ISO (yyyy-MM-dd). */
    public record DayDto(String date, Long shiftTypeId, String note) {
        public static DayDto from(DayEntry e) {
            return new DayDto(
                    e.getDate().toString(),
                    e.getShiftType() != null ? e.getShiftType().getId() : null,
                    e.getNote()
            );
        }
    }

    /** Upsert записи дня. Оба поля могут быть null. */
    public record DayUpsertRequest(Long shiftTypeId, String note) {}
}
