package ru.daniil.shifts.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.ImportantDay;
import ru.daniil.shifts.model.RepeatMode;
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
    public record DayDto(
            String date,
            Long shiftTypeId,
            String note,
            double overtimeHours,
            double timeOffHours,
            double overtimeBalanceHours
    ) {
        public static DayDto from(DayEntry e) {
            double overtime = e.getOvertimeHours();
            double timeOff = e.getTimeOffHours();
            return new DayDto(
                    e.getDate().toString(),
                    e.getShiftType() != null ? e.getShiftType().getId() : null,
                    e.getNote(),
                    overtime,
                    timeOff,
                    overtime - timeOff
            );
        }
    }

    /** Массовое заполнение графика от выбранной даты. Заметки и переработки при этом сохраняются. */
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

    /** Upsert записи дня. Все поля опциональны. */
    public record DayUpsertRequest(
            Long shiftTypeId,

            @Size(max = 20000, message = "Заметка слишком длинная: максимум 20 000 символов")
            String note,

            @DecimalMin(value = "0.0", message = "Переработка не может быть отрицательной")
            @DecimalMax(value = "100.0", message = "Переработка за день: максимум 100 часов")
            Double overtimeHours,

            @DecimalMin(value = "0.0", message = "Отгул не может быть отрицательным")
            @DecimalMax(value = "100.0", message = "Отгул за день: максимум 100 часов")
            Double timeOffHours
    ) {}

    /** Задача дня. */
    public record TaskDto(Long id, String date, String text, boolean done) {
        public static TaskDto from(DayTask task) {
            return new TaskDto(task.getId(), task.getDate().toString(), task.getText(), task.isDone());
        }
    }

    /** Создание задачи на день. */
    public record TaskCreateRequest(
            @NotBlank(message = "Дата задачи должна быть в формате yyyy-MM-dd")
            String date,

            @NotBlank(message = "Текст задачи не должен быть пустым")
            @Size(max = 500, message = "Текст задачи: максимум 500 символов")
            String text
    ) {}

    /** Обновление задачи. Поля опциональны. */
    public record TaskUpdateRequest(
            @Size(max = 500, message = "Текст задачи: максимум 500 символов")
            String text,
            Boolean done
    ) {}

    /** Важный день как настройка: дата-основа + режим повтора. */
    public record ImportantDayDto(
            Long id,
            String title,
            String date,
            RepeatMode repeatMode,
            String color
    ) {
        public static ImportantDayDto from(ImportantDay day) {
            return new ImportantDayDto(day.getId(), day.getTitle(), day.getDate().toString(), day.getRepeatMode(), day.getColor());
        }
    }

    /** Конкретное появление важного дня в диапазоне календаря. */
    public record ImportantDayOccurrenceDto(
            Long id,
            String date,
            String title,
            RepeatMode repeatMode,
            String color
    ) {}

    /** Создание важного дня. Для дней рождения обычно repeatMode = YEARLY. */
    public record ImportantDayCreateRequest(
            @NotBlank(message = "Название важного дня не должно быть пустым")
            @Size(max = 120, message = "Название важного дня: максимум 120 символов")
            String title,

            @NotBlank(message = "Дата важного дня должна быть в формате yyyy-MM-dd")
            String date,

            RepeatMode repeatMode,

            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color
    ) {}

    /** Обновление важного дня. Поля опциональны. */
    public record ImportantDayUpdateRequest(
            @Size(max = 120, message = "Название важного дня: максимум 120 символов")
            String title,
            String date,
            RepeatMode repeatMode,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color
    ) {}

    /** Сводка переработок за диапазон. */
    public record OvertimeSummaryDto(
            String from,
            String to,
            double overtimeHours,
            double timeOffHours,
            double balanceHours
    ) {}

    /** Строка журнала переработок: удобно для Android-экрана "История баланса". */
    public record OvertimeLedgerItemDto(
            String date,
            Long shiftTypeId,
            String shiftTypeName,
            double overtimeHours,
            double timeOffHours,
            double balanceHours,
            boolean hasNote
    ) {
        public static OvertimeLedgerItemDto from(DayEntry e) {
            double overtime = e.getOvertimeHours();
            double timeOff = e.getTimeOffHours();
            ShiftType shiftType = e.getShiftType();
            return new OvertimeLedgerItemDto(
                    e.getDate().toString(),
                    shiftType != null ? shiftType.getId() : null,
                    shiftType != null ? shiftType.getName() : null,
                    overtime,
                    timeOff,
                    overtime - timeOff,
                    e.getNote() != null && !e.getNote().isBlank()
            );
        }
    }

    /**
     * Удобный ответ для Android/PWA: одним запросом получаем диапазон дней,
     * доступные типы смен, задачи, важные дни и итоговый баланс переработок.
     */
    public record CalendarRangeDto(
            String from,
            String to,
            List<ShiftTypeDto> shiftTypes,
            List<DayDto> days,
            List<TaskDto> tasks,
            List<ImportantDayOccurrenceDto> importantDays,
            OvertimeSummaryDto overtime
    ) {}
}
