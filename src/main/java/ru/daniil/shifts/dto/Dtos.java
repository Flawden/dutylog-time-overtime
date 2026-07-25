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
            boolean sameTimezone
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
            ShiftIntervalDto shiftInterval
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
                    overtimeBalanceHours, version, updatedAt, null);
        }

        public static DayDto from(DayEntry e) {
            return from(e, null);
        }

        public static DayDto from(DayEntry e, ShiftIntervalDto shiftInterval) {
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
                    shiftInterval
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
    ) {}

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
            List<SubtaskDto> subtasks
    ) {
        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue) {
            this(id, date, text, done, category, tags, priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, List.of());
        }

        /** Source-compatible constructor for callers created before task tags were added. */
        public TaskDto(Long id, String date, String text, boolean done, String category,
                       TaskPriority priority, String dueDate, String dueTime,
                       boolean reminderEnabled, Integer reminderMinutesBefore, boolean overdue) {
            this(id, date, text, done, category, List.of(), priority, dueDate, dueTime,
                    reminderEnabled, reminderMinutesBefore, overdue, List.of());
        }

        public static TaskDto from(DayTask task) {
            return from(task, java.time.LocalDateTime.now());
        }

        public static TaskDto from(DayTask task, java.time.LocalDateTime now) {
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
                    isOverdue(task, now),
                    task.getSubtasks().stream()
                            .sorted(java.util.Comparator
                                    .comparingInt(TaskSubtask::getSortOrder)
                                    .thenComparing(subtask -> subtask.getId() == null ? Long.MAX_VALUE : subtask.getId()))
                            .map(SubtaskDto::from)
                            .toList()
            );
        }
        private static boolean isOverdue(DayTask task, java.time.LocalDateTime now) {
            if (task.isDone() || task.getDueDate() == null) return false;
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
            List<SubtaskInput> subtasks
    ) {
        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskCreateRequest(String date, String text, String category, List<String> tags,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(date, text, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null);
        }

        /** Source-compatible constructor for older internal callers. */
        public TaskCreateRequest(String date, String text, String category, TaskPriority priority,
                                 String dueDate, String dueTime, Boolean reminderEnabled,
                                 Integer reminderMinutesBefore) {
            this(date, text, category, null, priority, dueDate, dueTime, reminderEnabled, reminderMinutesBefore, null);
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

            Boolean completeSubtasks
    ) {
        /** Source-compatible constructor for callers created before subtasks were added. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 List<String> tags, TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(text, done, date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null);
        }

        /** Source-compatible constructor for older internal callers. */
        public TaskUpdateRequest(String text, Boolean done, String date, String category,
                                 TaskPriority priority, String dueDate, String dueTime,
                                 Boolean reminderEnabled, Integer reminderMinutesBefore) {
            this(text, done, date, category, null, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null, null);
        }
    }

    /** Saved task metadata used by category/tag suggestions. */
    public record TaskMetadataDto(List<String> categories, List<String> tags) {}

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
            List<SubtaskInput> subtasks
    ) {
        public InboxToTaskRequest(String date, String category, List<String> tags, TaskPriority priority,
                                  String dueDate, String dueTime, Boolean reminderEnabled,
                                  Integer reminderMinutesBefore) {
            this(date, category, tags, priority, dueDate, dueTime, reminderEnabled,
                    reminderMinutesBefore, null);
        }
    }

    public record InboxConversionDto(InboxItemDto inboxItem, TaskDto task) {}

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
            boolean exact,
            boolean reconstructed
    ) {
        /** Source-compatible constructor for pre-v27.9 callers. */
        public OvertimeUsageRefDto(Long usageId, String usageDate, double hours, String reason) {
            this(usageId, usageDate, hours, reason, (int) Math.round(hours * 60.0),
                    null, null, null, null, null, false, false);
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
            boolean legacyTimezoneRequired
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
                    calculated && startInstant == null);
        }
    }

    /** Списание отгула с расшифровкой, из каких начислений оно взяло часы. */
    public record OvertimeUsageDto(
            Long id,
            String usageDate,
            double hours,
            String reason,
            List<OvertimeAllocationDto> allocations,
            int minutes
    ) {
        public OvertimeUsageDto(Long id, String usageDate, double hours, String reason,
                                List<OvertimeAllocationDto> allocations) {
            this(id, usageDate, hours, reason, allocations, (int) Math.round(hours * 60.0));
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

    /** Полная бухгалтерия переработок: начисления, списания, остаток. */
    public record OvertimeAccountDto(
            double totalEarnedHours,
            double totalUsedHours,
            double balanceHours,
            List<OvertimeCreditRowDto> credits,
            List<OvertimeUsageDto> usages
    ) {}

    /** Страничный ответ для таблицы переработок: summary аккаунта + только текущая страница начислений. */
    public record OvertimeAccountPageDto(
            double totalEarnedHours,
            double totalUsedHours,
            double balanceHours,
            PageDto<OvertimeCreditRowDto> credits
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
            OvertimeSummaryDto overtime,
            OvertimeAccountDto overtimeAccount,
            NotificationSettingsDto notificationSettings,
            List<NotificationReminderDto> reminders,
            List<QuickScenarioDto> quickScenarios,
            List<ModuleDto> modules
    ) {}
}
