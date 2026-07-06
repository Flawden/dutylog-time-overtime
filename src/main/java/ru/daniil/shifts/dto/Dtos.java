package ru.daniil.shifts.dto;

import jakarta.validation.Valid;
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
import java.util.Map;

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

    /** Краткая информация о пользователе для мобильного клиента. */
    public record MobileUserDto(String username) {}

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

    /** Деталь списания: сколько часов было забрано из конкретного начисления. */
    public record OvertimeUsageRefDto(
            Long usageId,
            String usageDate,
            double hours,
            String reason
    ) {}

    /** Деталь начисления, из которого списали часы. */
    public record OvertimeAllocationDto(
            Long creditId,
            String workedDate,
            String timeRange,
            double hours,
            String reason
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
            List<OvertimeUsageRefDto> usages
    ) {}

    /** Списание отгула с расшифровкой, из каких начислений оно взяло часы. */
    public record OvertimeUsageDto(
            Long id,
            String usageDate,
            double hours,
            String reason,
            List<OvertimeAllocationDto> allocations
    ) {}

    /** Полная бухгалтерия переработок: начисления, списания, остаток. */
    public record OvertimeAccountDto(
            double totalEarnedHours,
            double totalUsedHours,
            double balanceHours,
            List<OvertimeCreditRowDto> credits,
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
            OvertimeAccountDto overtimeAccount
    ) {}
}
