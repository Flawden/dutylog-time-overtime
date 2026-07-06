package ru.daniil.shifts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

import java.util.List;

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
    public record ShiftTypeCreateRequest(
            @NotBlank(message = "Название смены не должно быть пустым")
            @Size(max = 60, message = "Название смены: максимум 60 символов")
            String name,

            @DecimalMin(value = "0.0", message = "Часы не могут быть отрицательными")
            @DecimalMax(value = "24.0", message = "Часы не могут быть больше 24")
            Double hours,

            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color
    ) {}

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


    /** Массовое заполнение графика от выбранной даты. Заметки при этом сохраняются. */
    public record DayFillRequest(
            @NotBlank(message = "Дата начала должна быть в формате yyyy-MM-dd")
            String startDate,

            @Min(value = 1, message = "Количество дней: минимум 1")
            @Max(value = 366, message = "Количество дней: максимум 366")
            Integer days,

            @NotEmpty(message = "Шаблон графика не должен быть пустым")
            List<Long> shiftTypeIds,

            Boolean overwriteExistingShift
    ) {}

    /** Upsert записи дня. Оба поля могут быть null. */
    public record DayUpsertRequest(
            Long shiftTypeId,

            @Size(max = 20000, message = "Заметка слишком длинная: максимум 20 000 символов")
            String note
    ) {}
}
