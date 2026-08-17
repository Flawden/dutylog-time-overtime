package ru.daniil.shifts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayTask;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.model.TaskSubtask;
import ru.daniil.shifts.model.ImportantDay;
import ru.daniil.shifts.model.ImportantEventType;
import ru.daniil.shifts.model.InboxItem;
import ru.daniil.shifts.model.InboxItemStatus;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.model.NotificationSettings;
import ru.daniil.shifts.model.QuickScenario;
import ru.daniil.shifts.model.ShiftType;

import java.util.List;
import java.util.Map;

/**
 * DTO-шки API. Вложенные records — легальный способ держать
 * несколько мелких типов в одном файле.
 */
public final class Dtos {
    private Dtos() {}


    /** Простая страница данных для больших списков: UI не получает тысячи строк одним ответом. */
    public record PageDto<T>(
            List<T> items,
            int page,
            int size,
            long total,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext
    ) {
        public static <T> PageDto<T> of(List<T> items, int page, int size, long total) {
            int safeSize = Math.max(1, size);
            int totalPages = total <= 0 ? 0 : (int) Math.ceil((double) total / safeSize);
            return new PageDto<>(
                    items == null ? List.of() : items,
                    Math.max(0, page),
                    safeSize,
                    Math.max(0, total),
                    totalPages,
                    page > 0,
                    totalPages > 0 && page + 1 < totalPages
            );
        }
    }


    /** Модуль приложения: UI может скрыть отключённые возможности, backend охраняет API. */
    public record ModuleDto(
            String key,
            String titleRu,
            String titleEn,
            String descriptionRu,
            String descriptionEn,
            boolean enabled,
            boolean locked,
            boolean defaultEnabled,
            List<String> dependencies,
            boolean hidden,
            String category,
            int order,
            List<String> uiSlots,
            List<String> apiPrefixes,
            List<String> offlineQueueTypes
    ) {}

    /** Безопасное обновление модулей: ключи берутся только из backend registry. */
    public record ModuleSettingsUpdateRequest(Map<String, Boolean> enabled) {}

    /** Тип смены наружу. */
    public record ShiftTypeDto(
            Long id,
            String name,
            double hours,
            String color,
            boolean builtin,
            String startTime,
            String endTime,
            int breakMinutes,
            double plannedHours,
            boolean notificationsEnabled,
            Integer notificationMinutesBefore
    ) {
        public static ShiftTypeDto from(ShiftType s) {
            return new ShiftTypeDto(
                    s.getId(),
                    s.getName(),
                    s.getHours(),
                    s.getColor(),
                    s.isBuiltin(),
                    s.getStartTime() != null ? s.getStartTime().toString() : null,
                    s.getEndTime() != null ? s.getEndTime().toString() : null,
                    s.getBreakMinutes(),
                    s.effectivePlannedHours(),
                    s.isNotificationsEnabled(),
                    s.getNotificationMinutesBefore()
            );
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
            String color,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время начала должно быть в формате HH:mm")
            String startTime,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время конца должно быть в формате HH:mm")
            String endTime,

            @Min(value = 0, message = "Обед не может быть отрицательным")
            @Max(value = 1440, message = "Обед не может быть больше 1440 минут")
            Integer breakMinutes,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "24.0", message = "Плановые часы не могут быть больше 24")
            Double plannedHours,

            Boolean notificationsEnabled,

            @Min(value = -1, message = "Напоминание смены не может быть меньше -1")
            @Max(value = 1440, message = "Напоминание смены: максимум 1440 минут")
            Integer notificationMinutesBefore
    ) {}

    /** Обновление типа смены. Все поля опциональны. */
    public record ShiftTypeUpdateRequest(
            @Size(max = 60, message = "Название смены: максимум 60 символов")
            String name,

            @DecimalMin(value = "0.0", message = "Часы не могут быть отрицательными")
            @DecimalMax(value = "24.0", message = "Часы не могут быть больше 24")
            Double hours,

            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время начала должно быть в формате HH:mm")
            String startTime,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время конца должно быть в формате HH:mm")
            String endTime,

            @Min(value = 0, message = "Обед не может быть отрицательным")
            @Max(value = 1440, message = "Обед не может быть больше 1440 минут")
            Integer breakMinutes,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "24.0", message = "Плановые часы не могут быть больше 24")
            Double plannedHours,

            Boolean notificationsEnabled,

            @Min(value = -1, message = "Напоминание смены не может быть меньше -1")
            @Max(value = 1440, message = "Напоминание смены: максимум 1440 минут")
            Integer notificationMinutesBefore
    ) {}

    /**
     * Absolute occurrence of a dated shift. The work-local projection owns the
     * schedule semantics; display-local values are read-only presentation.
     */
    public record ShiftIntervalDto(
            String startInstant,
            String endInstant,
            String workStart,
            String workEnd,
            String displayStart,
            String displayEnd,
            String workTimezone,
            String displayTimezone,
            int breakMinutes,
            long elapsedMinutes,
            long netMinutes,
            boolean crossesWorkMidnight,
            boolean crossesDisplayMidnight,
            boolean sameTimezone,
            boolean legacyLocal
    ) {
        /** Source-compatible constructor used by v27.8-v27.10 tests and adapters. */
        public ShiftIntervalDto(String startInstant, String endInstant, String workStart, String workEnd,
                                String displayStart, String displayEnd, String workTimezone, String displayTimezone,
                                int breakMinutes, long elapsedMinutes, long netMinutes, boolean crossesWorkMidnight,
                                boolean crossesDisplayMidnight, boolean sameTimezone) {
            this(startInstant, endInstant, workStart, workEnd, displayStart, displayEnd,
                    workTimezone, displayTimezone, breakMinutes, elapsedMinutes, netMinutes,
                    crossesWorkMidnight, crossesDisplayMidnight, sameTimezone, false);
        }
    }

    /** One immutable dated shift and its current calendar projection. */
    public record ShiftOccurrenceDto(
            Long dayEntryId,
            String sourceDate,
            Long shiftTypeId,
            String startInstant,
            String endInstant,
            String sourceStart,
            String sourceEnd,
            String displayStart,
            String displayEnd,
            String sourceTimezone,
            String displayTimezone,
            int breakMinutes,
            long elapsedMinutes,
            long netMinutes,
            boolean legacyLocal
    ) {}

    public record LegacyShiftOccurrenceDto(
            Long dayEntryId,
            String sourceDate,
            Long shiftTypeId,
            String shiftName,
            String localStart,
            String localEnd,
            String sourceTimezone,
            String projectedStart,
            String projectedEnd
    ) {}

    public record LegacyShiftMigrationPreviewDto(
            String sourceTimezone,
            int legacyCount,
            List<LegacyShiftOccurrenceDto> occurrences
    ) {}

    public record LegacyShiftMigrationRequest(
            @NotBlank(message = "Нужен исходный часовой пояс")
            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String sourceTimezone,
            @NotEmpty(message = "Выберите хотя бы одну смену")
            @Size(max = 5000, message = "За один раз можно привязать максимум 5000 смен")
            List<@NotNull Long> dayEntryIds
    ) {}

    public record LegacyTaskDeadlineDto(
            Long taskId,
            String text,
            String sourceDate,
            String sourceTime,
            String sourceTimezone,
            String projectedDate,
            String projectedTime,
            String targetTimezone,
            String dueInstant
    ) {}

    public record LegacyTaskDeadlineMigrationPreviewDto(
            String sourceTimezone,
            String targetTimezone,
            int legacyCount,
            List<LegacyTaskDeadlineDto> tasks
    ) {}

    public record LegacyTaskDeadlineMigrationRequest(
            @NotBlank(message = "Нужен исходный часовой пояс")
            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String sourceTimezone,
            @NotEmpty(message = "Выберите хотя бы одну задачу")
            @Size(max = 5000, message = "За один раз можно привязать максимум 5000 задач")
            List<@NotNull Long> taskIds
    ) {}


    public record DayNoteDto(
            Long id,
            String date,
            String title,
            String content,
            boolean pinned,
            int sortOrder,
            long version,
            String createdAt,
            String updatedAt
    ) {
        public static DayNoteDto from(ru.daniil.shifts.model.DayNote note) {
            return new DayNoteDto(
                    note.getId(),
                    note.getDate().toString(),
                    note.getTitle(),
                    note.getContent(),
                    note.isPinned(),
                    note.getSortOrder(),
                    note.getVersion(),
                    note.getCreatedAt() == null ? null : note.getCreatedAt().toString(),
                    note.getUpdatedAt() == null ? null : note.getUpdatedAt().toString()
            );
        }
    }

    public record DayNoteCreateRequest(
            @NotBlank(message = "Дата заметки обязательна")
            String date,
            @Size(max = 200, message = "Название заметки: максимум 200 символов")
            String title,
            @Size(max = 20000, message = "Заметка слишком длинная: максимум 20 000 символов")
            String content,
            Boolean pinned
    ) {}

    public record DayNoteUpdateRequest(
            @Size(max = 200, message = "Название заметки: максимум 200 символов")
            String title,
            @Size(max = 20000, message = "Заметка слишком длинная: максимум 20 000 символов")
            String content,
            Boolean pinned
    ) {}

    public record DayNoteMoveRequest(
            @NotBlank(message = "Нужно направление UP или DOWN")
            @Pattern(regexp = "(?i)UP|DOWN", message = "Направление должно быть UP или DOWN")
            String direction
    ) {}

    /** Запись дня наружу: дата в ISO (yyyy-MM-dd). */
    public record DayDto(
            String date,
            Long shiftTypeId,
            String note,
            String dayEmoji,
            double overtimeHours,
            double timeOffHours,
            double overtimeBalanceHours,
            long version,
            String updatedAt,
            ShiftIntervalDto shiftInterval,
            List<DayNoteDto> notes
    ) {
        /** Source-compatible constructor for clients/tests created before v27.8.0. */
        public DayDto(String date,
                      Long shiftTypeId,
                      String note,
                      String dayEmoji,
                      double overtimeHours,
                      double timeOffHours,
                      double overtimeBalanceHours,
                      long version,
                      String updatedAt) {
            this(date, shiftTypeId, note, dayEmoji, overtimeHours, timeOffHours,
                    overtimeBalanceHours, version, updatedAt, null, List.of());
        }

        public DayDto(String date,
                      Long shiftTypeId,
                      String note,
                      String dayEmoji,
                      double overtimeHours,
                      double timeOffHours,
                      double overtimeBalanceHours,
                      long version,
                      String updatedAt,
                      ShiftIntervalDto shiftInterval) {
            this(date, shiftTypeId, note, dayEmoji, overtimeHours, timeOffHours,
                    overtimeBalanceHours, version, updatedAt, shiftInterval, List.of());
        }

        public static DayDto from(DayEntry e) {
            return from(e, null, List.of());
        }

        public static DayDto from(DayEntry e, ShiftIntervalDto shiftInterval) {
            return from(e, shiftInterval, List.of());
        }

        public static DayDto from(DayEntry e, ShiftIntervalDto shiftInterval, List<DayNoteDto> notes) {
            double overtime = e.getOvertimeHours();
            double timeOff = e.getTimeOffHours();
            return new DayDto(
                    e.getDate().toString(),
                    e.getShiftType() != null ? e.getShiftType().getId() : null,
                    e.getNote(),
                    e.getDayEmoji(),
                    overtime,
                    timeOff,
                    overtime - timeOff,
                    e.getSyncVersion(),
                    e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null,
                    shiftInterval,
                    notes == null ? List.of() : List.copyOf(notes)
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

            @Size(max = 32, message = "Emoji дня: максимум 32 символа")
            String dayEmoji,

            @DecimalMin(value = "0.0", message = "Переработка не может быть отрицательной")
            @DecimalMax(value = "100.0", message = "Переработка за день: максимум 100 часов")
            Double overtimeHours,

            @DecimalMin(value = "0.0", message = "Отгул не может быть отрицательным")
            @DecimalMax(value = "100.0", message = "Отгул за день: максимум 100 часов")
            Double timeOffHours
    ) {}


    /** Быстрый сценарий заполнения формы переработки. */
    public record QuickScenarioDto(
            Long id,
            String name,
            String groupLabel,
            String description,
            String startMode,
            String endMode,
            int endOffsetMinutes,
            String endFixedTime,
            boolean endNextDay,
            int endDayOffset,
            String breakMode,
            int customBreakMinutes,
            String plannedMode,
            double customPlannedHours,
            String reasonTemplate,
            int sortOrder
    ) {
        public static QuickScenarioDto from(QuickScenario s) {
            return new QuickScenarioDto(
                    s.getId(),
                    s.getName(),
                    s.getGroupLabel(),
                    s.getDescription(),
                    s.getStartMode(),
                    s.getEndMode(),
                    s.getEndOffsetMinutes(),
                    s.getEndFixedTime() != null ? s.getEndFixedTime().toString() : null,
                    s.isEndNextDay(),
                    s.getEndDayOffset(),
                    s.getBreakMode(),
                    s.getCustomBreakMinutes(),
                    s.getPlannedMode(),
                    s.getCustomPlannedHours(),
                    s.getReasonTemplate(),
                    s.getSortOrder()
            );
        }
    }

    /** Создание быстрого сценария. */
    public record QuickScenarioCreateRequest(
            @NotBlank(message = "Название сценария не должно быть пустым")
            @Size(max = 80, message = "Название сценария: максимум 80 символов")
            String name,

            @Size(max = 40, message = "Группа сценария: максимум 40 символов")
            String groupLabel,

            @Size(max = 300, message = "Описание сценария: максимум 300 символов")
            String description,

            @Pattern(regexp = "SHIFT_START|SHIFT_END", message = "startMode: SHIFT_START или SHIFT_END")
            String startMode,

            @Pattern(regexp = "SHIFT_END|ADD_MINUTES|FIXED_TIME", message = "endMode: SHIFT_END, ADD_MINUTES или FIXED_TIME")
            String endMode,

            @Min(value = 0, message = "Смещение конца не может быть отрицательным")
            @Max(value = 4320, message = "Смещение конца: максимум 4320 минут")
            Integer endOffsetMinutes,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Фиксированное время конца должно быть в формате HH:mm")
            String endFixedTime,

            Boolean endNextDay,

            @Min(value = -2, message = "Смещение дня конца: минимум -2")
            @Max(value = 2, message = "Смещение дня конца: максимум 2")
            Integer endDayOffset,

            @Pattern(regexp = "ZERO|SHIFT|CUSTOM", message = "breakMode: ZERO, SHIFT или CUSTOM")
            String breakMode,

            @Min(value = 0, message = "Обед не может быть отрицательным")
            @Max(value = 1440, message = "Обед: максимум 1440 минут")
            Integer customBreakMinutes,

            @Pattern(regexp = "ZERO|SHIFT|CUSTOM", message = "plannedMode: ZERO, SHIFT или CUSTOM")
            String plannedMode,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "100.0", message = "Плановые часы: максимум 100")
            Double customPlannedHours,

            @Size(max = 300, message = "Причина сценария: максимум 300 символов")
            String reasonTemplate,

            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder
    ) {
        /** Source-compatible constructor for clients before day offsets were introduced. */
        public QuickScenarioCreateRequest(String name, String groupLabel, String description,
                                          String startMode, String endMode, Integer endOffsetMinutes,
                                          String endFixedTime, Boolean endNextDay,
                                          String breakMode, Integer customBreakMinutes,
                                          String plannedMode, Double customPlannedHours,
                                          String reasonTemplate, Integer sortOrder) {
            this(name, groupLabel, description, startMode, endMode, endOffsetMinutes,
                    endFixedTime, endNextDay, null, breakMode, customBreakMinutes,
                    plannedMode, customPlannedHours, reasonTemplate, sortOrder);
        }
    }

    /** Обновление быстрого сценария. Все поля опциональны. */
    public record QuickScenarioUpdateRequest(
            @Size(max = 80, message = "Название сценария: максимум 80 символов")
            String name,

            @Size(max = 40, message = "Группа сценария: максимум 40 символов")
            String groupLabel,

            @Size(max = 300, message = "Описание сценария: максимум 300 символов")
            String description,

            @Pattern(regexp = "SHIFT_START|SHIFT_END", message = "startMode: SHIFT_START или SHIFT_END")
            String startMode,

            @Pattern(regexp = "SHIFT_END|ADD_MINUTES|FIXED_TIME", message = "endMode: SHIFT_END, ADD_MINUTES или FIXED_TIME")
            String endMode,

            @Min(value = 0, message = "Смещение конца не может быть отрицательным")
            @Max(value = 4320, message = "Смещение конца: максимум 4320 минут")
            Integer endOffsetMinutes,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Фиксированное время конца должно быть в формате HH:mm")
            String endFixedTime,

            Boolean endNextDay,

            @Min(value = -2, message = "Смещение дня конца: минимум -2")
            @Max(value = 2, message = "Смещение дня конца: максимум 2")
            Integer endDayOffset,

            @Pattern(regexp = "ZERO|SHIFT|CUSTOM", message = "breakMode: ZERO, SHIFT или CUSTOM")
            String breakMode,

            @Min(value = 0, message = "Обед не может быть отрицательным")
            @Max(value = 1440, message = "Обед: максимум 1440 минут")
            Integer customBreakMinutes,

            @Pattern(regexp = "ZERO|SHIFT|CUSTOM", message = "plannedMode: ZERO, SHIFT или CUSTOM")
            String plannedMode,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "100.0", message = "Плановые часы: максимум 100")
            Double customPlannedHours,

            @Size(max = 300, message = "Причина сценария: максимум 300 символов")
            String reasonTemplate,

            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder
    ) {
        /** Source-compatible constructor for clients before day offsets were introduced. */
        public QuickScenarioUpdateRequest(String name, String groupLabel, String description,
                                          String startMode, String endMode, Integer endOffsetMinutes,
                                          String endFixedTime, Boolean endNextDay,
                                          String breakMode, Integer customBreakMinutes,
                                          String plannedMode, Double customPlannedHours,
                                          String reasonTemplate, Integer sortOrder) {
            this(name, groupLabel, description, startMode, endMode, endOffsetMinutes,
                    endFixedTime, endNextDay, null, breakMode, customBreakMinutes,
                    plannedMode, customPlannedHours, reasonTemplate, sortOrder);
        }
    }


    /** One ordered step in a reusable schedule cycle. */
    public record ScheduleTemplateStepDto(
            int position,
            Long shiftTypeId,
            String shiftTypeName,
            String shiftColor,
            boolean dayOff
    ) {
        public static ScheduleTemplateStepDto from(ru.daniil.shifts.model.ScheduleTemplateStep step) {
            ShiftType shift = step.getShiftType();
            return new ScheduleTemplateStepDto(
                    step.getPosition(), shift.getId(), shift.getName(), shift.getColor(),
                    shift.effectivePlannedHours() <= 0.0001
            );
        }
    }

    /** Reusable user-owned shift cycle. */
    public record ScheduleTemplateDto(
            Long id,
            String name,
            String description,
            String alignmentMode,
            boolean systemPreset,
            int sortOrder,
            List<ScheduleTemplateStepDto> steps,
            String createdAt,
            String updatedAt
    ) {
        public static ScheduleTemplateDto from(ru.daniil.shifts.model.ScheduleTemplate template) {
            return new ScheduleTemplateDto(
                    template.getId(), template.getName(), template.getDescription(), template.getAlignmentMode(),
                    template.isSystemPreset(), template.getSortOrder(),
                    template.getSteps().stream().map(ScheduleTemplateStepDto::from).toList(),
                    template.getCreatedAt() == null ? null : template.getCreatedAt().toString(),
                    template.getUpdatedAt() == null ? null : template.getUpdatedAt().toString()
            );
        }
    }

    public record ScheduleTemplateCreateRequest(
            @NotBlank(message = "Название шаблона не должно быть пустым")
            @Size(max = 100, message = "Название шаблона: максимум 100 символов")
            String name,
            @Size(max = 400, message = "Описание шаблона: максимум 400 символов")
            String description,
            @Pattern(regexp = "CYCLE_START|WEEKDAY", message = "alignmentMode: CYCLE_START или WEEKDAY")
            String alignmentMode,
            @NotEmpty(message = "Цикл должен содержать хотя бы один элемент")
            @Size(max = 64, message = "Цикл: максимум 64 элемента")
            List<@NotNull Long> shiftTypeIds,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder
    ) {}

    public record ScheduleTemplateUpdateRequest(
            @Size(max = 100, message = "Название шаблона: максимум 100 символов")
            String name,
            @Size(max = 400, message = "Описание шаблона: максимум 400 символов")
            String description,
            @Pattern(regexp = "CYCLE_START|WEEKDAY", message = "alignmentMode: CYCLE_START или WEEKDAY")
            String alignmentMode,
            @Size(min = 1, max = 64, message = "Цикл должен содержать от 1 до 64 элементов")
            List<@NotNull Long> shiftTypeIds,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder
    ) {}

    /** Preview/apply range for a schedule template. */
    public record ScheduleTemplateApplyRequest(
            @NotBlank(message = "Дата начала обязательна") String startDate,
            @NotBlank(message = "Дата окончания обязательна") String endDate,
            String anchorDate,
            Boolean overwriteExistingShift
    ) {}

    public record ScheduleTemplatePreviewItemDto(
            String date,
            int cyclePosition,
            Long shiftTypeId,
            String shiftTypeName,
            String shiftColor,
            Long existingShiftTypeId,
            String existingShiftTypeName,
            String action
    ) {}

    public record ScheduleTemplatePreviewDto(
            Long templateId,
            String templateName,
            String from,
            String to,
            String anchorDate,
            boolean overwriteExistingShift,
            int totalDays,
            int writeCount,
            int unchangedCount,
            int skippedCount,
            int conflictCount,
            List<ScheduleTemplatePreviewItemDto> items
    ) {}

    public record ScheduleTemplateApplyResultDto(
            Long templateId,
            String from,
            String to,
            int appliedCount,
            int unchangedCount,
            int skippedCount,
            int conflictCount,
            List<DayDto> days
    ) {}

    /** One projected occurrence from a companion layer after factual overrides are applied. */
    public record CalendarLayerEntryDto(
            Long layerId,
            String layerName,
            String layerColor,
            String sourceDate,
            String date,
            Long shiftTypeId,
            String shiftTypeName,
            String shiftColor,
            String sourceTimezone,
            String startInstant,
            String endInstant,
            String displayStart,
            String displayEnd,
            boolean timed,
            boolean dayOff,
            String sourceStartTime,
            String sourceEndTime,
            Long plannedShiftTypeId,
            String plannedShiftTypeName,
            String overrideKind,
            String overrideReason
    ) {}

    public record CalendarLayerDto(
            Long id,
            String name,
            String color,
            String timezone,
            boolean visible,
            int sortOrder,
            Long templateId,
            String templateName,
            String anchorDate,
            String startDate,
            String endDate,
            boolean readOnly,
            boolean scheduleEditable,
            List<CalendarLayerEntryDto> entries
    ) {}

    public record CalendarLayerOverrideRequest(
            @NotBlank(message = "Тип изменения дня обязателен")
            @Pattern(regexp = "WORK|OFF", message = "Тип изменения дня должен быть WORK или OFF")
            String kind,
            @Pattern(regexp = "TIME_OFF|VACATION|SICK|OTHER", message = "Неизвестная причина отсутствия")
            String reason,
            Long shiftTypeId,
            @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Время начала должно быть HH:mm")
            String startTime,
            @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Время окончания должно быть HH:mm")
            String endTime
    ) {}

    public record CalendarLayerOverrideDto(
            Long id,
            Long layerId,
            String sourceDate,
            String kind,
            String reason,
            Long shiftTypeId,
            String shiftTypeName,
            String startTime,
            String endTime
    ) {}

    public record CalendarLayerCreateRequest(
            @NotBlank(message = "Название слоя не должно быть пустым")
            @Size(max = 80, message = "Название слоя: максимум 80 символов")
            String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,
            @NotBlank(message = "Часовой пояс слоя обязателен")
            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String timezone,
            Boolean visible,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder,
            @NotNull(message = "Выберите шаблон") Long templateId,
            @NotBlank(message = "Опорная дата обязательна") String anchorDate,
            @NotBlank(message = "Дата начала обязательна") String startDate,
            String endDate
    ) {}

    public record CalendarLayerUpdateRequest(
            @Size(max = 80, message = "Название слоя: максимум 80 символов")
            String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,
            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String timezone,
            Boolean visible,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder,
            Long templateId,
            String anchorDate,
            String startDate,
            String endDate,
            Boolean clearEndDate
    ) {}

    /** One checklist item inside a task. Subtasks cannot contain children. */
    public record SubtaskDto(
            Long id,
            String text,
            boolean done,
            int sortOrder,
            String dueDate
    ) {
        /** Source-compatible constructor for callers created before subtask deadlines were added. */
        public SubtaskDto(Long id, String text, boolean done, int sortOrder) {
            this(id, text, done, sortOrder, null);
        }

        public static SubtaskDto from(TaskSubtask subtask) {
            return new SubtaskDto(
                    subtask.getId(),
                    subtask.getText(),
                    subtask.isDone(),
                    subtask.getSortOrder(),
                    subtask.getDueDate() != null ? subtask.getDueDate().toString() : null
            );
        }
    }

    /** Input model for creating or reconciling a task's one-level checklist. */
    public record SubtaskInput(
            Long id,
            @NotBlank(message = "Текст подзадачи не должен быть пустым")
            @Size(max = 300, message = "Текст подзадачи: максимум 300 символов")
            String text,
            Boolean done,
            @Min(value = 0, message = "Порядок подзадачи не может быть отрицательным")
            @Max(value = 10000, message = "Порядок подзадачи слишком большой")
            Integer sortOrder,
            String dueDate
    ) {
        /** Source-compatible constructor for callers created before subtask deadlines were added. */
        public SubtaskInput(Long id, String text, Boolean done, Integer sortOrder) {
            this(id, text, done, sortOrder, null);
        }
    }

    public record SubtaskUpdateRequest(
            @NotNull(message = "Не указан статус подзадачи")
            Boolean done
    ) {}

    /** Задача дня. */
    public record TaskDto(
            Long id,
            String date,
            String text,
            boolean done,
            String category,
            List<String> tags,
            TaskPriority priority,
            String dueDate,
            String dueTime,
            boolean reminderEnabled,
            Integer reminderMinutesBefore,
            boolean overdue,
            List<SubtaskDto> subtasks,
            String description,
            boolean deadlineAbsolute,
            String dueSourceTimezone,
            String dueSourceDate,
            String dueSourceTime,
            String project,
            boolean allDay,
            String scheduledStartDate,
            String scheduledStartTime,
            String scheduledEndDate,
            String scheduledEndTime,
            Long scheduledDurationMinutes,
            boolean scheduleAbsolute,
            String scheduledSourceTimezone,
            String scheduledSourceStartDate,
            String scheduledSourceStartTime,
            String scheduledSourceEndDate,
            String scheduledSourceEndTime
    ) {
        /** Source-compatible canonical constructor from v27.11.x. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue,
                       List<SubtaskDto> subtasks, String description, boolean deadlineAbsolute,
                       String dueSourceTimezone, String dueSourceDate, String dueSourceTime) {
            this(id, date, text, done, category, tags, priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, subtasks, description,
                    deadlineAbsolute, dueSourceTimezone, dueSourceDate, dueSourceTime,
                    null, true, date, null, null, null, null, false,
                    null, null, null, null, null);
        }

        /** Source-compatible canonical constructor from v27.10.x. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue,
                       List<SubtaskDto> subtasks, String description) {
            this(id, date, text, done, category, tags, priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, subtasks, description,
                    false, null, null, null);
        }

        /** Source-compatible canonical constructor from v27.9.x. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue,
                       List<SubtaskDto> subtasks) {
            this(id, date, text, done, category, tags, priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, subtasks, null);
        }

        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue) {
            this(id, date, text, done, category, tags, priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, List.of(), null);
        }

        /** Source-compatible constructor for callers created before task tags were added. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue) {
            this(id, date, text, done, category, List.of(), priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, List.of(), null);
        }

        public static TaskDto from(DayTask task) {
            return from(task, java.time.LocalDateTime.now(), java.time.Instant.now());
        }

        public static TaskDto from(DayTask task, java.time.LocalDateTime now) {
            return from(task, now, null);
        }

        public static TaskDto from(DayTask task, java.time.LocalDateTime now, java.time.Instant nowInstant) {
            Long durationMinutes = null;
            if (task.getScheduledStartInstant() != null && task.getScheduledEndInstant() != null) {
                durationMinutes = java.time.Duration.between(
                        task.getScheduledStartInstant(), task.getScheduledEndInstant()).toMinutes();
            }
            String startDate = task.getScheduledStartDate() != null
                    ? task.getScheduledStartDate().toString()
                    : task.getDate().toString();
            return new TaskDto(
                    task.getId(),
                    task.getDate().toString(),
                    task.getText(),
                    task.isDone(),
                    task.getCategory(),
                    List.copyOf(task.getTags()),
                    task.getPriority(),
                    task.getDueDate() != null ? task.getDueDate().toString() : null,
                    task.getDueTime() != null ? task.getDueTime().toString() : null,
                    task.isReminderEnabled(),
                    task.getReminderMinutesBefore(),
                    isOverdue(task, now, nowInstant),
                    task.getSubtasks().stream()
                            .sorted(java.util.Comparator
                                    .comparingInt(TaskSubtask::getSortOrder)
                                    .thenComparing(subtask -> subtask.getId() == null ? Long.MAX_VALUE : subtask.getId()))
                            .map(SubtaskDto::from)
                            .toList(),
                    task.getDescription(),
                    task.hasAbsoluteDeadline(),
                    task.getDueSourceTimezone(),
                    task.getDueSourceDate() != null ? task.getDueSourceDate().toString() : null,
                    task.getDueSourceTime() != null ? task.getDueSourceTime().toString() : null,
                    task.getProject(),
                    task.isAllDay(),
                    startDate,
                    task.getScheduledStartTime() != null ? task.getScheduledStartTime().toString() : null,
                    task.getScheduledEndDate() != null ? task.getScheduledEndDate().toString() : null,
                    task.getScheduledEndTime() != null ? task.getScheduledEndTime().toString() : null,
                    durationMinutes,
                    task.hasScheduledStart(),
                    task.getScheduledSourceTimezone(),
                    task.getScheduledSourceStartDate() != null ? task.getScheduledSourceStartDate().toString() : null,
                    task.getScheduledSourceStartTime() != null ? task.getScheduledSourceStartTime().toString() : null,
                    task.getScheduledSourceEndDate() != null ? task.getScheduledSourceEndDate().toString() : null,
                    task.getScheduledSourceEndTime() != null ? task.getScheduledSourceEndTime().toString() : null
            );
        }

        private static boolean isOverdue(DayTask task, java.time.LocalDateTime now, java.time.Instant nowInstant) {
            if (task.isDone() || task.getDueDate() == null) return false;
            if (task.getDueInstant() != null && nowInstant != null) {
                return task.getDueInstant().isBefore(nowInstant);
            }
            java.time.LocalDate today = now.toLocalDate();
            if (task.getDueDate().isBefore(today)) return true;
            if (task.getDueDate().isAfter(today) || task.getDueTime() == null) return false;
            return task.getDueTime().isBefore(now.toLocalTime());
        }
    }

    /** Создание задачи на день. */
    public record TaskCreateRequest(
            @NotBlank(message = "Дата задачи должна быть в формате yyyy-MM-dd")
            String date,

            @NotBlank(message = "Текст задачи не должен быть пустым")
            @Size(max = 500, message = "Текст задачи: максимум 500 символов")
            String text,

            @Size(max = 80, message = "Категория: максимум 80 символов")
            String category,

            @Size(max = 10, message = "Тегов задачи: максимум 10")
            List<@Size(max = 40, message = "Тег: максимум 40 символов") String> tags,

            TaskPriority priority,

            String dueDate,
            String dueTime,

            Boolean reminderEnabled,

            @Min(value = 0, message = "Напоминание не может быть отрицательным")
            @Max(value = 10080, message = "Напоминание: максимум 7 дней")
            Integer reminderMinutesBefore,

            @Valid
            @Size(max = 50, message = "Подзадач: максимум 50")
            List<SubtaskInput> subtasks,

            @Size(max = 4000, message = "Описание задачи: максимум 4000 символов")
            String description,

            @Size(max = 80, message = "Проект: максимум 80 символов")
            String project,

            Boolean allDay,
            String scheduledStartDate,
            String scheduledStartTime,
            String scheduledEndDate,
            String scheduledEndTime,

            @Min(value = 1, message = "Длительность должна быть больше нуля")
            @Max(value = 10080, message = "Длительность: максимум 7 дней")
            Integer scheduledDurationMinutes
    ) {
        /** Source-compatible canonical constructor from v27.10.x. */
        public TaskCreateRequest(String date, String text, String category, List<String> tags,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore,
                                 List<SubtaskInput> subtasks, String description) {
            this(date, text, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, description, null, true,
                    null, null, null, null, null);
        }
        /** Source-compatible canonical constructor from v27.9.x. */
        public TaskCreateRequest(String date, String text, String category, List<String> tags,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore,
                                 List<SubtaskInput> subtasks) {
            this(date, text, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, null);
        }
        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskCreateRequest(String date, String text, String category, List<String> tags,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(date, text, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null);
        }
        /** Source-compatible constructor for older internal callers. */
        public TaskCreateRequest(String date, String text, String category, TaskPriority priority,
                                 String dueDate, String dueTime, Boolean reminderEnabled,
                                 Integer reminderMinutesBefore) {
            this(date, text, category, null, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null);
        }
    }

    /** Обновление задачи. Поля опциональны. */
    public record TaskUpdateRequest(
            @Size(max = 500, message = "Текст задачи: максимум 500 символов")
            String text,
            Boolean done,
            String date,
            @Size(max = 80, message = "Категория: максимум 80 символов")
            String category,
            @Size(max = 10, message = "Тегов задачи: максимум 10")
            List<@Size(max = 40, message = "Тег: максимум 40 символов") String> tags,
            TaskPriority priority,
            String dueDate,
            String dueTime,
            Boolean reminderEnabled,
            @Min(value = 0, message = "Напоминание не может быть отрицательным")
            @Max(value = 10080, message = "Напоминание: максимум 7 дней")
            Integer reminderMinutesBefore,

            @Valid
            @Size(max = 50, message = "Подзадач: максимум 50")
            List<SubtaskInput> subtasks,

            Boolean completeSubtasks,

            @Size(max = 4000, message = "Описание задачи: максимум 4000 символов")
            String description,

            @Size(max = 80, message = "Проект: максимум 80 символов")
            String project,

            Boolean allDay,
            String scheduledStartDate,
            String scheduledStartTime,
            String scheduledEndDate,
            String scheduledEndTime,

            @Min(value = 1, message = "Длительность должна быть больше нуля")
            @Max(value = 10080, message = "Длительность: максимум 7 дней")
            Integer scheduledDurationMinutes
    ) {
        /** Source-compatible canonical constructor from v27.10.x. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore,
                                 List<SubtaskInput> subtasks, Boolean completeSubtasks, String description) {
            this(text, done, date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, completeSubtasks, description,
                    null, null, null, null, null, null, null);
        }
        /** Source-compatible canonical constructor from v27.9.x. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore,
                                 List<SubtaskInput> subtasks, Boolean completeSubtasks) {
            this(text, done, date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, completeSubtasks, null);
        }
        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(text, done, date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null, null);
        }
        /** Source-compatible constructor for older internal callers. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(text, done, date, category, null, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null, null);
        }
    }

    /** Saved task metadata used by category/tag/project suggestions. */
    public record TaskMetadataDto(List<String> categories, List<String> tags, List<String> projects) {
        public TaskMetadataDto(List<String> categories, List<String> tags) {
            this(categories, tags, List.of());
        }
    }

    /** A raw thought captured before the user decides how to organise it. */
    public record InboxItemDto(
            Long id,
            String text,
            InboxItemStatus status,
            String clientOperationId,
            String createdAt,
            String updatedAt,
            String resolvedAt
    ) {
        public static InboxItemDto from(InboxItem item) {
            return new InboxItemDto(
                    item.getId(),
                    item.getText(),
                    item.getStatus(),
                    item.getClientOperationId(),
                    item.getCreatedAt() != null ? item.getCreatedAt().toString() : null,
                    item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null,
                    item.getResolvedAt() != null ? item.getResolvedAt().toString() : null
            );
        }
    }

    public record InboxCreateRequest(
            @NotBlank(message = "Текст записи не должен быть пустым")
            @Size(max = 2000, message = "Текст записи: максимум 2000 символов")
            String text,
            @Size(max = 80, message = "Идентификатор операции: максимум 80 символов")
            String clientOperationId
    ) {}

    public record InboxUpdateRequest(
            @Size(max = 2000, message = "Текст записи: максимум 2000 символов")
            String text,
            Boolean archived
    ) {}

    public record InboxToTaskRequest(
            @NotBlank(message = "Дата задачи должна быть в формате yyyy-MM-dd")
            String date,
            @Size(max = 80, message = "Категория: максимум 80 символов")
            String category,
            @Size(max = 10, message = "Тегов задачи: максимум 10")
            List<@Size(max = 40, message = "Тег: максимум 40 символов") String> tags,
            TaskPriority priority,
            String dueDate,
            String dueTime,
            Boolean reminderEnabled,
            @Min(value = 0, message = "Напоминание не может быть отрицательным")
            @Max(value = 10080, message = "Напоминание: максимум 7 дней")
            Integer reminderMinutesBefore,
            @Valid
            @Size(max = 50, message = "Подзадач: максимум 50")
            List<SubtaskInput> subtasks,
            @Size(max = 4000, message = "Описание задачи: максимум 4000 символов")
            String description,
            @Size(max = 80, message = "Проект: максимум 80 символов")
            String project,
            Boolean allDay,
            String scheduledStartDate,
            String scheduledStartTime,
            String scheduledEndDate,
            String scheduledEndTime,
            @Min(value = 1, message = "Длительность должна быть больше нуля")
            @Max(value = 10080, message = "Длительность: максимум 7 дней")
            Integer scheduledDurationMinutes
    ) {
        /** Source-compatible canonical constructor from v27.10.x. */
        public InboxToTaskRequest(String date, String category, List<String> tags, TaskPriority priority,
                                  String dueDate, String dueTime, Boolean reminderEnabled,
                                  Integer reminderMinutesBefore, List<SubtaskInput> subtasks,
                                  String description) {
            this(date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, description, null, true,
                    null, null, null, null, null);
        }
        /** Source-compatible canonical constructor from v27.9.x. */
        public InboxToTaskRequest(String date, String category, List<String> tags, TaskPriority priority,
                                  String dueDate, String dueTime, Boolean reminderEnabled,
                                  Integer reminderMinutesBefore, List<SubtaskInput> subtasks) {
            this(date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, subtasks, null);
        }
        public InboxToTaskRequest(String date, String category, List<String> tags, TaskPriority priority,
                                  String dueDate, String dueTime, Boolean reminderEnabled,
                                  Integer reminderMinutesBefore) {
            this(date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null);
        }
    }

    public record InboxConversionDto(InboxItemDto inboxItem, TaskDto task) {}

    /** Important calendar entity; legacy fields remain first for source/API compatibility. */
    public record ImportantDayDto(
            Long id,
            String title,
            String date,
            RepeatMode repeatMode,
            String color,
            ImportantEventType eventType,
            String endDate,
            boolean allDay,
            String startTime,
            String endTime,
            String startInstant,
            String endInstant,
            String sourceTimezone,
            String place,
            String description,
            String icon,
            String category,
            List<Integer> reminders
    ) {
        /** Source-compatible constructor for callers created before v27.20.0. */
        public ImportantDayDto(Long id, String title, String date, RepeatMode repeatMode, String color) {
            this(id, title, date, repeatMode, color, ImportantEventType.IMPORTANT_DATE,
                    null, true, null, null, null, null, null,
                    null, null, null, null, List.of());
        }

        public static ImportantDayDto from(ImportantDay day) {
            return new ImportantDayDto(
                    day.getId(),
                    day.getTitle(),
                    day.getDate().toString(),
                    day.getRepeatMode(),
                    day.getColor(),
                    day.getEventType(),
                    day.getEndDate() == null ? null : day.getEndDate().toString(),
                    day.isAllDay(),
                    day.getStartTime() == null ? null : day.getStartTime().toString(),
                    day.getEndTime() == null ? null : day.getEndTime().toString(),
                    day.getStartInstant() == null ? null : day.getStartInstant().toString(),
                    day.getEndInstant() == null ? null : day.getEndInstant().toString(),
                    day.getSourceTimezone(),
                    day.getPlace(),
                    day.getDescription(),
                    day.getIcon(),
                    day.getCategory(),
                    parseReminderOffsets(day.getReminderOffsets())
            );
        }
    }

    /** Concrete projected occurrence inside a calendar range. */
    public record ImportantDayOccurrenceDto(
            Long id,
            String date,
            String title,
            RepeatMode repeatMode,
            String color,
            ImportantEventType eventType,
            String startDate,
            String endDate,
            boolean allDay,
            String startTime,
            String endTime,
            String startInstant,
            String endInstant,
            String sourceTimezone,
            String displayTimezone,
            String place,
            String description,
            String icon,
            String category,
            List<Integer> reminders
    ) {
        /** Source-compatible constructor for v27.19.x code and tests. */
        public ImportantDayOccurrenceDto(Long id, String date, String title,
                                         RepeatMode repeatMode, String color) {
            this(id, date, title, repeatMode, color, ImportantEventType.IMPORTANT_DATE,
                    date, date, true, null, null, null, null, null, null,
                    null, null, null, null, List.of());
        }
    }

    /** Create an important date, timed event or multi-day period. */
    public record ImportantDayCreateRequest(
            @NotBlank(message = "Название важного события не должно быть пустым")
            @Size(max = 120, message = "Название важного события: максимум 120 символов")
            String title,

            @NotBlank(message = "Дата начала должна быть в формате yyyy-MM-dd")
            String date,

            RepeatMode repeatMode,

            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,

            ImportantEventType eventType,
            String endDate,
            Boolean allDay,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время начала должно быть в формате HH:mm")
            String startTime,

            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время окончания должно быть в формате HH:mm")
            String endTime,

            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String sourceTimezone,

            @Size(max = 240, message = "Место: максимум 240 символов")
            String place,

            @Size(max = 10000, message = "Описание: максимум 10 000 символов")
            String description,

            @Size(max = 32, message = "Значок: максимум 32 символа")
            String icon,

            @Size(max = 80, message = "Категория: максимум 80 символов")
            String category,

            @Size(max = 10, message = "Можно задать максимум 10 напоминаний")
            List<@Min(value = 0, message = "Напоминание не может быть отрицательным")
                 @Max(value = 525600, message = "Напоминание: максимум за один год") Integer> reminders
    ) {
        /** Source-compatible constructor for the historical important-day API. */
        public ImportantDayCreateRequest(String title, String date, RepeatMode repeatMode, String color) {
            this(title, date, repeatMode, color, ImportantEventType.IMPORTANT_DATE,
                    null, true, null, null, null, null, null, null, null, List.of());
        }
    }

    /** Partial update. Null fields keep the stored value. */
    public record ImportantDayUpdateRequest(
            @Size(max = 120, message = "Название важного события: максимум 120 символов")
            String title,
            String date,
            RepeatMode repeatMode,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,
            ImportantEventType eventType,
            String endDate,
            Boolean allDay,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время начала должно быть в формате HH:mm")
            String startTime,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время окончания должно быть в формате HH:mm")
            String endTime,
            @Size(max = 80, message = "Часовой пояс: максимум 80 символов")
            String sourceTimezone,
            @Size(max = 240, message = "Место: максимум 240 символов")
            String place,
            @Size(max = 10000, message = "Описание: максимум 10 000 символов")
            String description,
            @Size(max = 32, message = "Значок: максимум 32 символа")
            String icon,
            @Size(max = 80, message = "Категория: максимум 80 символов")
            String category,
            @Size(max = 10, message = "Можно задать максимум 10 напоминаний")
            List<@Min(value = 0, message = "Напоминание не может быть отрицательным")
                 @Max(value = 525600, message = "Напоминание: максимум за один год") Integer> reminders
    ) {
        /** Source-compatible constructor for the historical important-day API. */
        public ImportantDayUpdateRequest(String title, String date, RepeatMode repeatMode, String color) {
            this(title, date, repeatMode, color, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    private static List<Integer> parseReminderOffsets(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try { return Integer.valueOf(value); }
                    catch (NumberFormatException ignored) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /** Настройки уведомлений и напоминаний пользователя. */
    public record NotificationSettingsDto(
            boolean browserNotificationsEnabled,
            boolean shiftRemindersEnabled,
            int shiftReminderMinutesBefore,
            boolean tomorrowDigestEnabled,
            String tomorrowDigestTime,
            boolean taskRemindersEnabled,
            String taskReminderTime,
            boolean importantDayRemindersEnabled,
            int importantDayDaysBefore,
            String importantDayReminderTime,
            String updatedAt
    ) {
        public static NotificationSettingsDto from(NotificationSettings s) {
            return new NotificationSettingsDto(
                    s.isBrowserNotificationsEnabled(),
                    s.isShiftRemindersEnabled(),
                    s.getShiftReminderMinutesBefore(),
                    s.isTomorrowDigestEnabled(),
                    s.getTomorrowDigestTime().toString(),
                    s.isTaskRemindersEnabled(),
                    s.getTaskReminderTime().toString(),
                    s.isImportantDayRemindersEnabled(),
                    s.getImportantDayDaysBefore(),
                    s.getImportantDayReminderTime().toString(),
                    s.getUpdatedAt().toString()
            );
        }
    }

    /** Обновление настроек уведомлений. Все поля опциональны. */
    public record NotificationSettingsUpdateRequest(
            Boolean browserNotificationsEnabled,
            Boolean shiftRemindersEnabled,
            @Min(value = 0, message = "Напоминание перед сменой не может быть отрицательным")
            @Max(value = 1440, message = "Напоминание перед сменой: максимум 1440 минут")
            Integer shiftReminderMinutesBefore,
            Boolean tomorrowDigestEnabled,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время дайджеста должно быть в формате HH:mm")
            String tomorrowDigestTime,
            Boolean taskRemindersEnabled,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время напоминаний о задачах должно быть в формате HH:mm")
            String taskReminderTime,
            Boolean importantDayRemindersEnabled,
            @Min(value = 0, message = "Дней до важного дня не может быть меньше 0")
            @Max(value = 366, message = "Дней до важного дня: максимум 366")
            Integer importantDayDaysBefore,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время напоминаний о важных днях должно быть в формате HH:mm")
            String importantDayReminderTime
    ) {}

    /** Current absolute clock and its projections into the two user timezones. */
    public record TimeContextDto(
            String nowInstant,
            String workTimezone,
            String displayTimezone,
            String workLocalDateTime,
            String displayLocalDateTime,
            String workDate,
            String displayDate,
            String workOffset,
            String displayOffset,
            boolean sameTimezone
    ) {}

    /** Рассчитанное напоминание для веба, Android или будущего Telegram-бота. */
    public record NotificationReminderDto(
            String id,
            String type,
            String sourceDate,
            String remindAt,
            String title,
            String details,
            int priority,
            String remindAtInstant,
            String workTimezone,
            String displayAt,
            String displayTimezone
    ) {
        /**
         * Compatibility constructor for tests and delivery adapters that only need
         * the user-local wall clock value. Production reminder calculation also
         * supplies remindAtInstant so browser clients never reinterpret it in the
         * device timezone.
         */
        public NotificationReminderDto(String id,
                                       String type,
                                       String sourceDate,
                                       String remindAt,
                                       String title,
                                       String details,
                                       int priority) {
            this(id, type, sourceDate, remindAt, title, details, priority,
                    null, null, null, null);
        }

        /** Compatibility constructor used by v27.6.x reminder producers. */
        public NotificationReminderDto(String id,
                                       String type,
                                       String sourceDate,
                                       String remindAt,
                                       String title,
                                       String details,
                                       int priority,
                                       String remindAtInstant) {
            this(id, type, sourceDate, remindAt, title, details, priority,
                    remindAtInstant, null, null, null);
        }
    }



    /** Запрос логина для Android/PWA API. */
    public record MobileLoginRequest(
            @NotBlank(message = "Имя пользователя не должно быть пустым")
            String username,

            @NotBlank(message = "Пароль не должен быть пустым")
            String password,

            @Size(max = 120, message = "Название устройства: максимум 120 символов")
            String deviceName
    ) {}

    /** Запрос обновления access token через refresh token. */
    public record MobileRefreshRequest(
            @NotBlank(message = "Нужен refreshToken")
            String refreshToken
    ) {}

    /** Запрос выхода мобильного клиента. */
    public record MobileLogoutRequest(String refreshToken) {}

    /** Краткая информация о пользователе и его временном контексте для мобильного клиента. */
    public record MobileUserDto(String username, String workTimezone, String displayTimezone) {
        public MobileUserDto(String username) {
            this(username, null, null);
        }

        public static MobileUserDto from(AppUser user) {
            return new MobileUserDto(user.getUsername(), user.getWorkTimezone(), user.getDisplayTimezone());
        }
    }

    /** Ответ мобильной авторизации. */
    public record MobileTokenResponse(
            String tokenType,
            String accessToken,
            String accessExpiresAt,
            String refreshToken,
            String refreshExpiresAt,
            MobileUserDto user
    ) {}

    /** Активная мобильная сессия / устройство. */
    public record MobileAuthTokenDto(
            Long id,
            String deviceName,
            String createdAt,
            String lastUsedAt,
            String refreshExpiresAt,
            boolean revoked,
            boolean active
    ) {}

    /** Первый крупный ответ для Android: профиль + диапазон календаря + serverTime. */
    public record MobileBootstrapDto(
            String serverTime,
            MobileUserDto user,
            CalendarRangeDto calendar
    ) {}

    /** Изменение одного дня для пакетной мобильной синхронизации. */
    public record MobileDayChangeRequest(
            @NotBlank(message = "Дата дня должна быть в формате yyyy-MM-dd")
            String date,
            Long shiftTypeId,
            Boolean clearShiftType,

            @Size(max = 20000, message = "Заметка слишком длинная: максимум 20 000 символов")
            String note,
            Boolean clearNote,

            @Size(max = 32, message = "Emoji дня: максимум 32 символа")
            String dayEmoji,
            Boolean clearDayEmoji,

            @DecimalMin(value = "0.0", message = "Переработка не может быть отрицательной")
            @DecimalMax(value = "100.0", message = "Переработка за день: максимум 100 часов")
            Double overtimeHours,

            @DecimalMin(value = "0.0", message = "Отгул не может быть отрицательным")
            @DecimalMax(value = "100.0", message = "Отгул за день: максимум 100 часов")
            Double timeOffHours
    ) {}

    /** Пакет изменений с Android. Пока синхронизируем дни; задачи и важные дни идут обычными API. */
    public record MobileSyncRequest(
            @Size(max = 366, message = "За один sync можно отправить максимум 366 изменений дней")
            List<@Valid MobileDayChangeRequest> days
    ) {}

    /** Результат пакетной синхронизации. */
    public record MobileSyncResultDto(
            String serverTime,
            List<DayDto> days,
            Map<String, String> warnings
    ) {}


    /** Registration request for the stable Android API v1. */
    public record MobileRegisterRequest(
            @NotBlank(message = "Имя пользователя не должно быть пустым")
            String username,
            @NotBlank(message = "Пароль не должен быть пустым")
            String password,
            @Pattern(regexp = "ru|en", message = "languagePreference: ru или en")
            String languagePreference,
            @Size(max = 120, message = "Название устройства: максимум 120 символов")
            String deviceName
    ) {}

    /** Stable bootstrap envelope for /api/v1/mobile. */
    public record MobileV1BootstrapDto(
            String apiVersion,
            String serverTime,
            MobileUserDto user,
            CalendarRangeDto calendar
    ) {}

    /** One idempotent day mutation from the Android offline queue. */
    public record MobileV1DayOperationRequest(
            @NotBlank(message = "Нужен operationId")
            @Size(max = 64, message = "operationId: максимум 64 символа")
            @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "operationId содержит недопустимые символы")
            String operationId,

            @NotNull(message = "Нужен baseVersion")
            @Min(value = 0, message = "baseVersion не может быть отрицательным")
            Long baseVersion,

            @NotNull(message = "Нужно изменение day")
            @Valid MobileDayChangeRequest day
    ) {}

    /** Batch of idempotent operations for the stable Android API v1. */
    public record MobileV1SyncRequest(
            @NotEmpty(message = "Список operations не должен быть пустым")
            @Size(max = 366, message = "За один sync можно отправить максимум 366 операций")
            List<@NotNull @Valid MobileV1DayOperationRequest> operations
    ) {}

    /** Per-operation result: one bad item never hides successful neighbours. */
    public record MobileSyncItemResultDto(
            String operationId,
            String status,
            String entityType,
            String entityId,
            Long serverVersion,
            DayDto entity,
            String errorCode,
            String message
    ) {}

    /** Stable sync response for Android API v1. */
    public record MobileV1SyncResultDto(
            String apiVersion,
            String serverTime,
            List<MobileSyncItemResultDto> items
    ) {}

    /**
     * Начисление переработки. Можно передать либо готовые hours, либо интервал startDateTime/endDateTime,
     * тогда сервер сам посчитает: длительность - обед - плановые часы.
     */
    public record OvertimeCreditCreateRequest(
            @NotBlank(message = "Дата переработки должна быть в формате yyyy-MM-dd")
            String date,

            @Size(max = 50, message = "Время переработки: максимум 50 символов")
            String timeRange,

            /** Формат datetime-local / ISO: 2026-05-04T20:00 */
            String startDateTime,

            /** Формат datetime-local / ISO: 2026-05-05T08:00 */
            String endDateTime,

            @DecimalMin(value = "0", message = "Обед не может быть отрицательным")
            @DecimalMax(value = "1440", message = "Обед не может быть больше 1440 минут")
            Integer breakMinutes,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "100.0", message = "Плановые часы: максимум 100")
            Double plannedHours,

            @DecimalMin(value = "0.01", message = "Переработка должна быть больше 0")
            @DecimalMax(value = "100.0", message = "Переработка за запись: максимум 100 часов")
            Double hours,

            @Size(max = 1000, message = "Причина переработки: максимум 1000 символов")
            String reason
    ) {}

    /** Обновление начисления переработки. Все поля опциональны; пустые start/end переводят запись в ручной режим. */
    public record OvertimeCreditUpdateRequest(
            String date,

            @Size(max = 50, message = "Время переработки: максимум 50 символов")
            String timeRange,

            String startDateTime,
            String endDateTime,

            @DecimalMin(value = "0", message = "Обед не может быть отрицательным")
            @DecimalMax(value = "1440", message = "Обед не может быть больше 1440 минут")
            Integer breakMinutes,

            @DecimalMin(value = "0.0", message = "Плановые часы не могут быть отрицательными")
            @DecimalMax(value = "100.0", message = "Плановые часы: максимум 100")
            Double plannedHours,

            @DecimalMin(value = "0.01", message = "Переработка должна быть больше 0")
            @DecimalMax(value = "100.0", message = "Переработка за запись: максимум 100 часов")
            Double hours,

            @Size(max = 1000, message = "Причина переработки: максимум 1000 символов")
            String reason
    ) {}

    /** Списание часов переработки в отгул. Распределяется по старым начислениям автоматически. */
    public record OvertimeUsageCreateRequest(
            @NotBlank(message = "Дата списания должна быть в формате yyyy-MM-dd")
            String date,

            @DecimalMin(value = "0.01", message = "Списание должно быть больше 0")
            @DecimalMax(value = "100.0", message = "Списание за запись: максимум 100 часов")
            Double hours,

            @Size(max = 1000, message = "Причина списания: максимум 1000 символов")
            String reason
    ) {}

    /** Обновление списания отгула. Если часы изменились, распределение FIFO пересобирается заново. */
    public record OvertimeUsageUpdateRequest(
            String date,

            @DecimalMin(value = "0.01", message = "Списание должно быть больше 0")
            @DecimalMax(value = "100.0", message = "Списание за запись: максимум 100 часов")
            Double hours,

            @Size(max = 1000, message = "Причина списания: максимум 1000 символов")
            String reason
    ) {}

    /** Деталь списания: сколько минут и какой именно участок были забраны из начисления. */
    public record OvertimeUsageRefDto(
            Long usageId,
            String usageDate,
            double hours,
            String reason,
            int minutes,
            String startInstant,
            String endInstant,
            String displayStart,
            String displayEnd,
            String sourceTimezone,
            int allocationPartIndex,
            int allocationPartCount,
            boolean exact,
            boolean reconstructed
    ) {
        /** Source-compatible constructor for pre-v27.9 callers. */
        public OvertimeUsageRefDto(Long usageId, String usageDate, double hours, String reason) {
            this(usageId, usageDate, hours, reason, (int) Math.round(hours * 60.0),
                    null, null, null, null, null, 1, 1, false, false);
        }
    }

    /** Деталь начисления, из которого списали часы, с точным FIFO-интервалом. */
    public record OvertimeAllocationDto(
            Long creditId,
            String workedDate,
            String timeRange,
            double hours,
            String reason,
            int minutes,
            String startInstant,
            String endInstant,
            String displayStart,
            String displayEnd,
            String sourceTimezone,
            boolean exact,
            boolean reconstructed
    ) {
        /** Source-compatible constructor for pre-v27.9 callers. */
        public OvertimeAllocationDto(Long creditId, String workedDate, String timeRange, double hours, String reason) {
            this(creditId, workedDate, timeRange, hours, reason, (int) Math.round(hours * 60.0),
                    null, null, null, null, null, false, false);
        }
    }

    /** Canonical server-side preview of the overtime editor interval. */
    public record OvertimeCreditPreviewDto(
            boolean calculated,
            int elapsedMinutes,
            double elapsedHours,
            int breakMinutes,
            int plannedMinutes,
            double plannedHours,
            int creditedMinutes,
            double creditedHours,
            String sourceTimezone,
            String startInstant,
            String endInstant
    ) {}

    /**
     * Current-timezone daily projection metadata for one overtime credit row.
     *
     * <p>The persisted credit and FIFO allocation remain absolute. A single
     * credit may therefore produce several display rows when its credited
     * interval crosses midnight in the user's current IANA timezone.</p>
     */
    public record OvertimeDailyProjectionDto(
            String sourceWorkedDate,
            String sourceTimeRange,
            int partIndex,
            int partCount,
            int dayRowIndex,
            int dayRowCount,
            double dayEarnedHours,
            double dayUsedHours,
            double dayRemainingHours,
            double sourceCreditHours,
            double sourceUsedHours,
            double sourceRemainingHours,
            boolean exact
    ) {}

    /** Строка таблицы начислений переработки. */
    public record OvertimeCreditRowDto(
            Long id,
            String workedDate,
            String timeRange,
            String startDateTime,
            String endDateTime,
            int breakMinutes,
            double plannedHours,
            boolean calculated,
            double hours,
            String reason,
            double usedHours,
            double remainingHours,
            List<OvertimeUsageRefDto> usages,
            String startInstant,
            String endInstant,
            String sourceTimezone,
            String displayStart,
            String displayEnd,
            String displayTimezone,
            int creditedMinutes,
            String creditedStartInstant,
            String creditedEndInstant,
            String creditedDisplayStart,
            String creditedDisplayEnd,
            boolean migratedFromLegacy,
            boolean legacyTimezoneRequired,
            OvertimeDailyProjectionDto projection
    ) {
        /** Source-compatible constructor for v27.8 service/tests. */
        public OvertimeCreditRowDto(Long id, String workedDate, String timeRange,
                                    String startDateTime, String endDateTime, int breakMinutes,
                                    double plannedHours, boolean calculated, double hours, String reason,
                                    double usedHours, double remainingHours, List<OvertimeUsageRefDto> usages,
                                    String startInstant, String endInstant, String sourceTimezone,
                                    String displayStart, String displayEnd, String displayTimezone) {
            this(id, workedDate, timeRange, startDateTime, endDateTime, breakMinutes, plannedHours,
                    calculated, hours, reason, usedHours, remainingHours, usages,
                    startInstant, endInstant, sourceTimezone, displayStart, displayEnd, displayTimezone,
                    (int) Math.round(hours * 60.0), null, null, null, null, false,
                    calculated && startInstant == null,
                    new OvertimeDailyProjectionDto(
                            workedDate, timeRange, 1, 1, 1, 1,
                            hours, usedHours, remainingHours,
                            hours, usedHours, remainingHours,
                            startInstant != null));
        }
    }

    /** Списание отгула с расшифровкой, из каких начислений оно взяло часы. */
    public record OvertimeUsageDto(
            Long id,
            String usageDate,
            double hours,
            String reason,
            List<OvertimeAllocationDto> allocations,
            int minutes,
            String sourceKind,
            Long sourceAbsenceId,
            boolean editable,
            String postingState,
            boolean reserved
    ) {
        public OvertimeUsageDto(Long id, String usageDate, double hours, String reason,
                                List<OvertimeAllocationDto> allocations) {
            this(id, usageDate, hours, reason, allocations, (int) Math.round(hours * 60.0),
                    "MANUAL", null, true, "POSTED", false);
        }

        public OvertimeUsageDto(Long id, String usageDate, double hours, String reason,
                                List<OvertimeAllocationDto> allocations, int minutes) {
            this(id, usageDate, hours, reason, allocations, minutes, "MANUAL", null, true,
                    "POSTED", false);
        }
    }


    /** Legacy overtime row that still needs an explicit source timezone. */
    public record LegacyOvertimeCreditDto(
            Long id,
            String workedDate,
            String startDateTime,
            String endDateTime,
            String timeRange,
            double hours,
            int minutes,
            String reason,
            boolean migratable,
            String blockedReason,
            String sourceTimezone,
            String projectedStart,
            String projectedEnd,
            String creditedStart,
            String creditedEnd
    ) {}

    public record LegacyOvertimeMigrationRequest(
            List<Long> creditIds,
            String sourceTimezone
    ) {}

    public record LegacyOvertimeMigrationPreviewDto(
            String sourceTimezone,
            int requestedCount,
            int migratableCount,
            int blockedCount,
            List<LegacyOvertimeCreditDto> credits
    ) {}

    public record LegacyOvertimeMigrationResultDto(
            int migratedCount,
            int skippedCount,
            OvertimeAccountDto account
    ) {}

    /** One legacy MANUAL overtime usage that can be promoted into the canonical absence ledger. */
    public record LegacyOvertimeUsageMigrationItemDto(
            Long usageId,
            String usageDate,
            double hours,
            int minutes,
            String reason,
            String inferredCoverage,
            boolean plannedShiftPresent,
            int plannedShiftMinutes,
            boolean migratable,
            String blockedReason
    ) {}

    /** Empty usageIds means all currently migratable legacy usages. */
    public record LegacyOvertimeUsageMigrationRequest(
            List<Long> usageIds
    ) {}

    public record LegacyOvertimeUsageMigrationPreviewDto(
            int totalCount,
            int fullDayCount,
            int hoursOnlyCount,
            int blockedCount,
            List<LegacyOvertimeUsageMigrationItemDto> usages
    ) {}

    public record LegacyOvertimeUsageMigrationResultDto(
            int migratedCount,
            int skippedCount,
            List<Long> absenceIds
    ) {}

    /** Полная бухгалтерия переработок: начисления, списания, остаток. */
    public record OvertimeAccountDto(
            double totalEarnedHours,
            double totalUsedHours,
            double balanceHours,
            List<OvertimeCreditRowDto> credits,
            List<OvertimeUsageDto> usages
    ) {}

    /**
     * Страничный ответ для экрана переработок: summary аккаунта, текущая страница
     * начислений и полный список списаний. Списания не восстанавливаются из
     * paged credit rows: один отгул может быть распределён между несколькими
     * начислениями и страницами.
     */
    public record OvertimeAccountPageDto(
            double totalEarnedHours,
            double totalUsedHours,
            double balanceHours,
            PageDto<OvertimeCreditRowDto> credits,
            List<OvertimeUsageDto> usages
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


    /** Vacation policy is user-configurable and deliberately country-neutral. */
    public record VacationSettingsDto(
            int annualAllowanceDays,
            int carryoverDays,
            String countMode,
            int workYearStartMonth,
            int workYearStartDay,
            String updatedAt,
            double timeOffBalanceHours,
            double defaultTimeOffDayHours
    ) {
        public static VacationSettingsDto from(ru.daniil.shifts.model.VacationSettings settings) {
            return new VacationSettingsDto(
                    settings.getAnnualAllowanceDays(), settings.getCarryoverDays(), settings.getCountMode(),
                    settings.getWorkYearStartMonth(), settings.getWorkYearStartDay(),
                    settings.getUpdatedAt() == null ? null : settings.getUpdatedAt().toString(),
                    settings.getTimeOffBalanceMinutes() / 60.0,
                    settings.getDefaultTimeOffDayMinutes() / 60.0
            );
        }
    }

    public record VacationSettingsUpdateRequest(
            @Min(value = 0, message = "Годовая норма не может быть отрицательной")
            @Max(value = 366, message = "Годовая норма: максимум 366 дней")
            Integer annualAllowanceDays,
            @Min(value = 0, message = "Перенос не может быть отрицательным")
            @Max(value = 366, message = "Перенос: максимум 366 дней")
            Integer carryoverDays,
            @Pattern(regexp = "CALENDAR_DAYS|WEEKDAYS", message = "countMode: CALENDAR_DAYS или WEEKDAYS")
            String countMode,
            @Min(value = 1, message = "Месяц начала рабочего года: минимум 1")
            @Max(value = 12, message = "Месяц начала рабочего года: максимум 12")
            Integer workYearStartMonth,
            @Min(value = 1, message = "День начала рабочего года: минимум 1")
            @Max(value = 28, message = "День начала рабочего года: максимум 28")
            Integer workYearStartDay,
            @DecimalMin(value = "0.0", message = "Баланс отгулов не может быть отрицательным")
            @DecimalMax(value = "10000.0", message = "Баланс отгулов: максимум 10 000 часов")
            Double timeOffBalanceHours,
            @DecimalMin(value = "0.25", message = "Полный отгул: минимум 15 минут")
            @DecimalMax(value = "24.0", message = "Полный отгул: максимум 24 часа")
            Double defaultTimeOffDayHours
    ) {
        public VacationSettingsUpdateRequest(Integer annualAllowanceDays, Integer carryoverDays, String countMode,
                                             Integer workYearStartMonth, Integer workYearStartDay) {
            this(annualAllowanceDays, carryoverDays, countMode, workYearStartMonth, workYearStartDay, null, null);
        }
    }

    public record AbsenceTypeDto(
            Long id,
            String name,
            String color,
            boolean countsAgainstAllowance,
            boolean systemPreset,
            String systemCode,
            int sortOrder,
            String balancePolicy,
            boolean fullDayReplacesShift
    ) {
        public static AbsenceTypeDto from(ru.daniil.shifts.model.AbsenceType type) {
            return new AbsenceTypeDto(type.getId(), type.getName(), type.getColor(),
                    type.isCountsAgainstAllowance(), type.isSystemPreset(), type.getSystemCode(), type.getSortOrder(),
                    type.getBalancePolicy(), type.isFullDayReplacesShift());
        }
    }

    public record AbsenceTypeCreateRequest(
            @NotBlank(message = "Название типа отсутствия не должно быть пустым")
            @Size(max = 80, message = "Название типа отсутствия: максимум 80 символов")
            String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,
            Boolean countsAgainstAllowance,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder,
            @Pattern(regexp = "VACATION_DAYS|TIME_OFF_HOURS|NONE", message = "balancePolicy: VACATION_DAYS, TIME_OFF_HOURS или NONE")
            String balancePolicy,
            Boolean fullDayReplacesShift
    ) {
        public AbsenceTypeCreateRequest(String name, String color, Boolean countsAgainstAllowance, Integer sortOrder) {
            this(name, color, countsAgainstAllowance, sortOrder, null, null);
        }
    }

    public record AbsenceTypeUpdateRequest(
            @Size(max = 80, message = "Название типа отсутствия: максимум 80 символов")
            String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Цвет должен быть в формате #RRGGBB")
            String color,
            Boolean countsAgainstAllowance,
            @Min(value = 0, message = "Порядок не может быть отрицательным")
            @Max(value = 10000, message = "Порядок слишком большой")
            Integer sortOrder,
            @Pattern(regexp = "VACATION_DAYS|TIME_OFF_HOURS|NONE", message = "balancePolicy: VACATION_DAYS, TIME_OFF_HOURS или NONE")
            String balancePolicy,
            Boolean fullDayReplacesShift
    ) {
        public AbsenceTypeUpdateRequest(String name, String color, Boolean countsAgainstAllowance, Integer sortOrder) {
            this(name, color, countsAgainstAllowance, sortOrder, null, null);
        }
    }

    public record VacationSummaryDto(
            String workYearStart,
            String workYearEnd,
            int annualAllowanceDays,
            int carryoverDays,
            int availableDays,
            int plannedDays,
            int remainingDays,
            String countMode,
            int timeOffAvailableMinutes,
            int timeOffPlannedMinutes,
            int timeOffRemainingMinutes
    ) {}

    public record AbsencePeriodDto(
            Long id,
            Long typeId,
            String typeName,
            String typeColor,
            String systemCode,
            boolean countsAgainstAllowance,
            String title,
            String startDate,
            String endDate,
            String status,
            String note,
            int calendarDays,
            int countedDays,
            int shiftConflictCount,
            String createdAt,
            String updatedAt,
            String balancePolicy,
            String coverage,
            String startTime,
            String endTime,
            int chargedMinutes,
            boolean replacesShift,
            String compensationPolicy,
            int compensatedMinutes,
            Long linkedOvertimeUsageId
    ) {}

    public record AbsencePeriodCreateRequest(
            @NotNull(message = "Выберите тип отсутствия") Long typeId,
            @Size(max = 120, message = "Название периода: максимум 120 символов") String title,
            @NotBlank(message = "Дата начала обязательна") String startDate,
            @NotBlank(message = "Дата окончания обязательна") String endDate,
            @Pattern(regexp = "DRAFT|PLANNED|SUBMITTED|APPROVED|REJECTED|CANCELLED|COMPLETED", message = "Некорректный статус отсутствия") String status,
            @Size(max = 1000, message = "Комментарий: максимум 1000 символов") String note,
            @Pattern(regexp = "FULL_DAY|PARTIAL", message = "coverage: FULL_DAY или PARTIAL") String coverage,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время начала должно быть в формате HH:mm") String startTime,
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Время окончания должно быть в формате HH:mm") String endTime,
            @Pattern(regexp = "VACATION_ALLOWANCE|OVERTIME_BANK|SICK_PAY|UNPAID|NONE", message = "Некорректный источник компенсации") String compensationPolicy
    ) {
        public AbsencePeriodCreateRequest(Long typeId, String title, String startDate, String endDate, String status, String note) {
            this(typeId, title, startDate, endDate, status, note, null, null, null, null);
        }
    }

    public record AbsencePeriodUpdateRequest(
            Long typeId,
            @Size(max = 120, message = "Название периода: максимум 120 символов") String title,
            String startDate,
            String endDate,
            @Pattern(regexp = "DRAFT|PLANNED|SUBMITTED|APPROVED|REJECTED|CANCELLED|COMPLETED", message = "Некорректный статус отсутствия") String status,
            @Size(max = 1000, message = "Комментарий: максимум 1000 символов") String note,
            Boolean clearTitle,
            Boolean clearNote,
            @Pattern(regexp = "FULL_DAY|PARTIAL|HOURS_ONLY", message = "coverage: FULL_DAY, PARTIAL или HOURS_ONLY") String coverage,
            String startTime,
            String endTime,
            Boolean clearTimes,
            @Pattern(regexp = "VACATION_ALLOWANCE|OVERTIME_BANK|SICK_PAY|UNPAID|NONE", message = "Некорректный источник компенсации") String compensationPolicy
    ) {
        public AbsencePeriodUpdateRequest(Long typeId, String title, String startDate, String endDate,
                                          String status, String note, Boolean clearTitle, Boolean clearNote) {
            this(typeId, title, startDate, endDate, status, note, clearTitle, clearNote, null, null, null, null, null);
        }
    }

    public record AbsencePreviewRequest(
            @NotNull(message = "Выберите тип отсутствия") Long typeId,
            @NotBlank(message = "Дата начала обязательна") String startDate,
            @NotBlank(message = "Дата окончания обязательна") String endDate,
            Long excludePeriodId,
            @Pattern(regexp = "FULL_DAY|PARTIAL|HOURS_ONLY", message = "coverage: FULL_DAY, PARTIAL или HOURS_ONLY") String coverage,
            String startTime,
            String endTime,
            @Pattern(regexp = "VACATION_ALLOWANCE|OVERTIME_BANK|SICK_PAY|UNPAID|NONE", message = "Некорректный источник компенсации") String compensationPolicy
    ) {
        public AbsencePreviewRequest(Long typeId, String startDate, String endDate, Long excludePeriodId) {
            this(typeId, startDate, endDate, excludePeriodId, null, null, null, null);
        }
    }

    public record AbsencePreviewItemDto(
            String date,
            boolean weekend,
            boolean counted,
            boolean shiftConflict,
            Long existingAbsenceId,
            String existingAbsenceTitle,
            String action,
            String plannedShiftName,
            String plannedShiftColor,
            int plannedShiftMinutes,
            boolean replacesShift
    ) {}

    public record AbsencePreviewDto(
            Long typeId,
            String typeName,
            String startDate,
            String endDate,
            int calendarDays,
            int countedDays,
            int shiftConflictCount,
            int absenceConflictCount,
            String workYearStart,
            String workYearEnd,
            int availableDays,
            int plannedBefore,
            int projectedPlanned,
            int remainingAfter,
            boolean exceedsAllowance,
            int exceededBy,
            List<AbsencePreviewItemDto> items,
            String balancePolicy,
            String coverage,
            int durationMinutes,
            int timeOffAvailableMinutes,
            int timeOffPlannedBefore,
            int timeOffProjected,
            int timeOffRemainingAfter,
            String compensationPolicy
    ) {}

    /** One day-sized projection of an absence period into the calendar. */
    public record AbsenceOccurrenceDto(
            Long periodId,
            Long typeId,
            String typeName,
            String typeColor,
            String systemCode,
            String title,
            String date,
            String startDate,
            String endDate,
            String status,
            boolean countedDay,
            boolean shiftConflict,
            String balancePolicy,
            String coverage,
            String startTime,
            String endTime,
            int chargedMinutes,
            boolean replacesShift,
            String plannedShiftName,
            String plannedShiftColor,
            int plannedShiftMinutes,
            String compensationPolicy,
            int compensatedMinutes,
            Long linkedOvertimeUsageId
    ) {
        /** Source-compatible constructor for the v27.25 plan/fact projection. */
        public AbsenceOccurrenceDto(Long periodId, Long typeId, String typeName, String typeColor, String systemCode,
                                    String title, String date, String startDate, String endDate, String status,
                                    boolean countedDay, boolean shiftConflict, String balancePolicy, String coverage,
                                    String startTime, String endTime, int chargedMinutes, boolean replacesShift,
                                    String plannedShiftName, String plannedShiftColor, int plannedShiftMinutes) {
            this(periodId, typeId, typeName, typeColor, systemCode, title, date, startDate, endDate, status,
                    countedDay, shiftConflict, balancePolicy, coverage, startTime, endTime, chargedMinutes,
                    replacesShift, plannedShiftName, plannedShiftColor, plannedShiftMinutes,
                    defaultCompensation(systemCode), 0, null);
        }

        /** Source-compatible constructor for v27.23.x iCalendar tests and integrations. */
        public AbsenceOccurrenceDto(Long periodId, Long typeId, String typeName, String typeColor, String systemCode,
                                    String title, String date, String startDate, String endDate, String status,
                                    boolean countedDay, boolean shiftConflict) {
            this(periodId, typeId, typeName, typeColor, systemCode, title, date, startDate, endDate, status,
                    countedDay, shiftConflict, countsPolicy(systemCode), "FULL_DAY", null, null, 0, true,
                    null, null, 0, defaultCompensation(systemCode), 0, null);
        }

        private static String countsPolicy(String systemCode) {
            return "VACATION".equalsIgnoreCase(systemCode) ? "VACATION_DAYS" : "NONE";
        }

        private static String defaultCompensation(String systemCode) {
            if ("VACATION".equalsIgnoreCase(systemCode)) return "VACATION_ALLOWANCE";
            if ("TIME_OFF".equalsIgnoreCase(systemCode)) return "OVERTIME_BANK";
            if ("SICK".equalsIgnoreCase(systemCode)) return "SICK_PAY";
            if ("UNPAID".equalsIgnoreCase(systemCode)) return "UNPAID";
            return "NONE";
        }
    }

    public record AbsenceTypeSummaryDto(
            Long typeId,
            String typeName,
            String typeColor,
            String systemCode,
            String balancePolicy,
            int fullDays,
            int partialMinutes,
            int chargedMinutes
    ) {}

    public record VacationPlannerDto(
            VacationSettingsDto settings,
            VacationSummaryDto summary,
            List<Integer> durationPresets,
            List<AbsenceTypeDto> types,
            List<AbsencePeriodDto> absences,
            List<AbsenceOccurrenceDto> occurrences,
            List<AbsenceTypeSummaryDto> typeSummaries
    ) {}

    /** One day in the unified plan → fact → compensation read model. */
    public record TimeCompensationDayDto(
            String date,
            int plannedMinutes,
            int workedMinutes,
            int absenceMinutes,
            int overtimeEarnedMinutes,
            int overtimeUsedMinutes,
            int compensatedMinutes,
            int vacationDays,
            int sickMinutes,
            int unpaidMinutes,
            String factLabel,
            String compensationLabel,
            List<Long> absenceIds,
            String actualSource,
            List<Long> actualWorkIntervalIds
    ) {}

    /** Payroll-ready monthly foundation without applying money rules yet. */
    public record TimeCompensationSummaryDto(
            String from,
            String to,
            int plannedMinutes,
            int workedMinutes,
            int absenceMinutes,
            int overtimeEarnedMinutes,
            int overtimeUsedMinutes,
            int overtimeBalanceMinutes,
            int compensatedMinutes,
            int vacationDays,
            int sickMinutes,
            int unpaidMinutes,
            int overtimeReservedMinutes,
            int overtimePostedMinutes,
            boolean integrityHealthy,
            boolean periodClosed,
            List<TimeCompensationDayDto> days
    ) {}

    /** Immutable audit entry for one time/compensation movement. */
    public record TimeLedgerEntryDto(
            Long id,
            String entryKind,
            String sourceKind,
            Long sourceId,
            String effectiveDate,
            int signedMinutes,
            String postingState,
            Long reversalOfId,
            String reason,
            String createdAt
    ) {}

    public record LedgerIntegrityIssueDto(
            String code,
            String severity,
            String message,
            String sourceKind,
            Long sourceId
    ) {}

    /** Integrity and reservation projection that Payroll Foundation can trust. */
    public record LedgerIntegrityDto(
            String from,
            String to,
            boolean healthy,
            int reservedMinutes,
            int postedMinutes,
            int reversedMinutes,
            int orphanUsageCount,
            int allocationMismatchCount,
            List<LedgerIntegrityIssueDto> issues,
            List<TimeLedgerEntryDto> entries,
            List<AccountingPeriodDto> periods
    ) {}

    public record AccountingPeriodDto(
            String month,
            String status,
            String closedAt,
            String updatedAt
    ) {}

    public record LedgerAdjustmentRequest(
            @NotBlank(message = "Месяц обязателен") String month,
            @NotNull(message = "Количество минут обязательно") Integer signedMinutes,
            @Size(max = 500, message = "Причина: максимум 500 символов") String reason
    ) {}

    public record ActualWorkIntervalDto(
            Long id,
            String workDate,
            String startTime,
            String endTime,
            int workedMinutes,
            int breakMinutes,
            String note,
            String createdAt,
            String updatedAt
    ) {}

    public record ActualWorkIntervalRequest(
            @NotBlank(message = "Дата обязательна") String workDate,
            @NotBlank(message = "Время начала обязательно") String startTime,
            @NotBlank(message = "Время окончания обязательно") String endTime,
            @Min(value = 0, message = "Перерыв не может быть отрицательным")
            @Max(value = 1440, message = "Перерыв не может быть больше 1440 минут")
            Integer breakMinutes,
            @Size(max = 500, message = "Комментарий: максимум 500 символов") String note
    ) {
        public ActualWorkIntervalRequest(String workDate, String startTime, String endTime, String note) {
            this(workDate, startTime, endTime, null, note);
        }
    }

    /** Local production-calendar rule; schedule/norm and payroll effects are independent. */
    public record ProductionCalendarDayUpdateRequest(
            @NotBlank(message = "Тип дня обязателен")
            @Pattern(regexp = "(?i)NORMAL|HOLIDAY|TRANSFERRED_DAY_OFF|TRANSFERRED_WORKDAY|SHORTENED_DAY",
                    message = "Некорректный тип производственного дня")
            String dayKind,
            @NotBlank(message = "Влияние на норму обязательно")
            @Pattern(regexp = "(?i)NONE|NORM_OVERRIDE", message = "Некорректное влияние на норму")
            String scheduleEffect,
            @Min(value = 0, message = "Норма дня не может быть отрицательной")
            @Max(value = 1440, message = "Норма дня не может быть больше 1440 минут")
            Integer normMinutesOverride,
            @NotBlank(message = "Категория оплаты обязательна")
            @Pattern(regexp = "(?i)NONE|HOLIDAY", message = "Некорректная категория оплаты")
            String payrollEffect,
            @Size(max = 120, message = "Название: максимум 120 символов") String label
    ) {}

    public record ProductionCalendarDayDto(
            String date,
            String dayKind,
            String scheduleEffect,
            Integer normMinutesOverride,
            String payrollEffect,
            String label,
            String sourceType,
            String sourceRef,
            boolean localOverride,
            int baseNormMinutes,
            int productionNormMinutes,
            int adjustmentMinutes
    ) {}

    public record ProductionCalendarMonthDto(
            String month,
            int baseNormMinutes,
            int productionNormMinutes,
            int adjustmentMinutes,
            int holidayReductionMinutes,
            int shortenedReductionMinutes,
            int transferredAdjustmentMinutes,
            int affectedDays,
            int scheduleCoverageDays,
            boolean scheduleCoverageComplete,
            List<ProductionCalendarDayDto> days
    ) {}

    /** Native per-date truth used by Calendar/Today as the human command surface. */
    public record WorkdayTruthDto(
            String date,
            String shiftName,
            String scheduledStartTime,
            String scheduledEndTime,
            int scheduledBreakMinutes,
            int baseNormMinutes,
            int requiredNormMinutes,
            ProductionCalendarDayDto productionCalendar,
            boolean explicitActual,
            int actualMinutes,
            int absenceMinutes,
            int overtimeEarnedMinutes,
            int overtimeUsedMinutes,
            String factLabel,
            List<ActualWorkIntervalDto> actualWork
    ) {}

    /** Per-user money settings. All values are stored in minor currency units. */
    public record PayrollSettingsDto(
            String currencyCode,
            long hourlyRateMinor,
            String updatedAt
    ) {}

    public record PayrollSettingsUpdateRequest(
            @NotBlank(message = "Код валюты обязателен")
            @Pattern(regexp = "[A-Za-z]{3}", message = "Код валюты должен состоять из трёх букв")
            String currencyCode,

            @NotNull(message = "Почасовая ставка обязательна")
            @Min(value = 0, message = "Ставка не может быть отрицательной")
            @Max(value = 1000000000L, message = "Ставка слишком велика")
            Long hourlyRateMinor
    ) {}

    /** Append-only manual money movement for one payroll month. */
    public record PayrollAdjustmentRequest(
            @NotBlank(message = "Месяц обязателен") String month,
            @NotBlank(message = "Тип корректировки обязателен")
            @Pattern(regexp = "(?i)ADDITION|DEDUCTION", message = "Тип должен быть ADDITION или DEDUCTION")
            String adjustmentType,
            @NotNull(message = "Сумма обязательна")
            @Min(value = 1, message = "Сумма должна быть положительной")
            @Max(value = 1000000000000L, message = "Сумма слишком велика")
            Long amountMinor,
            @NotBlank(message = "Название обязательно")
            @Size(max = 120, message = "Название: максимум 120 символов") String title,
            @Size(max = 500, message = "Комментарий: максимум 500 символов") String note
    ) {}

    public record PayrollAdjustmentDto(
            Long id,
            String month,
            String adjustmentType,
            long amountMinor,
            String title,
            String note,
            String createdAt
    ) {}

    /** Transparent source-time and money projection before it is frozen into a revision. */
    public record PayrollPreviewDto(
            String month,
            String currencyCode,
            long hourlyRateMinor,
            int plannedMinutes,
            int workedMinutes,
            int vacationMinutes,
            int sickMinutes,
            int overtimeCompensatedMinutes,
            int unpaidMinutes,
            int timeAdjustmentMinutes,
            int paidAbsenceMinutes,
            int payableMinutes,
            long basePayMinor,
            long additionsMinor,
            long deductionsMinor,
            long totalPayMinor
    ) {}

    /** Immutable versioned payroll snapshot of a closed accounting month. */
    public record PayrollSnapshotDto(
            Long id,
            String month,
            int revision,
            String currencyCode,
            long hourlyRateMinor,
            int plannedMinutes,
            int workedMinutes,
            int vacationMinutes,
            int sickMinutes,
            int overtimeCompensatedMinutes,
            int unpaidMinutes,
            int timeAdjustmentMinutes,
            int paidAbsenceMinutes,
            int payableMinutes,
            long basePayMinor,
            long additionsMinor,
            long deductionsMinor,
            long totalPayMinor,
            String sourcePeriodClosedAt,
            String sourceIntegrityCheckedAt,
            String calculationHash,
            String createdAt,
            Long supersededById
    ) {}

    /** One Payroll Foundation workspace payload; preview is available before final calculation. */
    public record PayrollPeriodDto(
            String month,
            boolean periodClosed,
            boolean integrityHealthy,
            boolean canCalculate,
            String blockingReason,
            PayrollSettingsDto settings,
            ProductionCalendarMonthDto productionCalendar,
            PayrollPreviewDto preview,
            List<PayrollAdjustmentDto> adjustments,
            PayrollSnapshotDto latestSnapshot,
            List<PayrollSnapshotDto> snapshots
    ) {}

    /** Private read-only iCalendar subscription state. Raw tokens are returned only on issue/rotation. */
    public record CalendarSyncStatusDto(
            boolean active,
            String tokenHint,
            String createdAt,
            String rotatedAt,
            int feedPastDays,
            int feedFutureDays,
            List<String> entities
    ) {}

    /** One-time response after creating or rotating a private calendar feed token. */
    public record CalendarSubscriptionDto(
            boolean active,
            String tokenHint,
            String createdAt,
            String rotatedAt,
            String subscriptionUrl,
            int feedPastDays,
            int feedFutureDays,
            List<String> entities
    ) {}

    /**
     * Удобный ответ для Android/PWA: одним запросом получаем диапазон дней,
     * доступные типы смен, задачи, важные дни и итоговый баланс переработок.
     */
    public record CalendarRangeDto(
            String from,
            String to,
            List<ShiftTypeDto> shiftTypes,
            List<DayDto> days,
            List<ShiftOccurrenceDto> shiftOccurrences,
            List<TaskDto> tasks,
            List<ImportantDayOccurrenceDto> importantDays,
            List<AbsenceOccurrenceDto> absences,
            OvertimeSummaryDto overtime,
            OvertimeAccountDto overtimeAccount,
            NotificationSettingsDto notificationSettings,
            List<NotificationReminderDto> reminders,
            List<QuickScenarioDto> quickScenarios,
            List<CalendarLayerDto> calendarLayers,
            List<ModuleDto> modules
    ) {
        /** Compatibility constructor for code written before calendar layers. */
        public CalendarRangeDto(String from, String to, List<ShiftTypeDto> shiftTypes, List<DayDto> days,
                                List<ShiftOccurrenceDto> shiftOccurrences, List<TaskDto> tasks,
                                List<ImportantDayOccurrenceDto> importantDays, OvertimeSummaryDto overtime,
                                OvertimeAccountDto overtimeAccount, NotificationSettingsDto notificationSettings,
                                List<NotificationReminderDto> reminders, List<QuickScenarioDto> quickScenarios,
                                List<ModuleDto> modules) {
            this(from, to, shiftTypes, days, shiftOccurrences, tasks, importantDays, List.of(), overtime, overtimeAccount,
                    notificationSettings, reminders, quickScenarios, List.of(), modules);
        }

        /** Compatibility constructor for code written before shift occurrences. */
        public CalendarRangeDto(String from, String to, List<ShiftTypeDto> shiftTypes, List<DayDto> days,
                                List<TaskDto> tasks, List<ImportantDayOccurrenceDto> importantDays,
                                OvertimeSummaryDto overtime, OvertimeAccountDto overtimeAccount,
                                NotificationSettingsDto notificationSettings, List<NotificationReminderDto> reminders,
                                List<QuickScenarioDto> quickScenarios, List<ModuleDto> modules) {
            this(from, to, shiftTypes, days, List.of(), tasks, importantDays, List.of(), overtime, overtimeAccount,
                    notificationSettings, reminders, quickScenarios, List.of(), modules);
        }
    }
}
