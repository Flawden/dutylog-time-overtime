package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.MobileDayChangeRequest;
import ru.daniil.shifts.dto.Dtos.MobileSyncItemResultDto;
import ru.daniil.shifts.dto.Dtos.MobileV1DayOperationRequest;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncRequest;
import ru.daniil.shifts.dto.Dtos.MobileV1SyncResultDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.MobileSyncOperation;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.MobileSyncOperationRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Android API v1 idempotent offline-queue processor. */
@Service
public class MobileSyncService {
    public static final String API_VERSION = "v1";

    private final DayEntryService dayEntryService;
    private final DayEntryRepository days;
    private final MobileSyncOperationRepository operations;
    private final ModuleService moduleService;
    private final int retentionDays;

    public MobileSyncService(DayEntryService dayEntryService,
                             DayEntryRepository days,
                             MobileSyncOperationRepository operations,
                             ModuleService moduleService,
                             @Value("${dutylog.mobile.sync.idempotency-retention-days:90}") int retentionDays) {
        this.dayEntryService = dayEntryService;
        this.days = days;
        this.operations = operations;
        this.moduleService = moduleService;
        this.retentionDays = Math.max(7, retentionDays);
    }

    @Transactional
    public MobileV1SyncResultDto sync(AppUser user, MobileV1SyncRequest request) {
        if (request == null || request.operations() == null || request.operations().isEmpty()) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Список operations не должен быть пустым");
        }
        List<MobileSyncItemResultDto> results = new ArrayList<>();
        for (MobileV1DayOperationRequest operation : request.operations()) {
            results.add(processDayOperation(user, operation));
        }
        return new MobileV1SyncResultDto(API_VERSION, Instant.now().toString(), results);
    }


    /** Keep the idempotency ledger bounded without storing user content. */
    @Scheduled(fixedDelayString = "${dutylog.mobile.sync.cleanup-delay-ms:86400000}", initialDelay = 60000)
    @Transactional
    public void cleanupExpiredOperations() {
        operations.deleteByCreatedAtBefore(Instant.now().minusSeconds(retentionDays * 86400L));
    }

    private MobileSyncItemResultDto processDayOperation(AppUser user,
                                                         MobileV1DayOperationRequest request) {
        if (request == null
                || request.operationId() == null
                || request.operationId().isBlank()
                || request.baseVersion() == null
                || request.day() == null) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Некорректная операция синхронизации");
        }

        String operationId = request.operationId();
        var previous = operations.findByOwnerAndOperationId(user, operationId);
        if (previous.isPresent()) {
            return replay(previous.get());
        }

        MobileDayChangeRequest change = request.day();
        LocalDate date;
        try {
            date = dayEntryService.parseDate(change.date(),
                    "Дата дня должна быть в формате yyyy-MM-dd");
        } catch (ApiException ex) {
            MobileSyncOperation stored = operations.save(new MobileSyncOperation(
                    user, operationId, "day", safeEntityKey(change.date()), "REJECTED",
                    null, ex.getCode(), safeMessage(ex.getMessage())));
            return fromStored(stored, null);
        }

        DayEntry current = days.findByOwnerAndDate(user, date).orElse(null);
        long currentVersion = current == null ? 0L : current.getSyncVersion();

        if (!hasMutation(change)) {
            String message = "Операция не содержит изменений";
            MobileSyncOperation stored = operations.save(new MobileSyncOperation(
                    user, operationId, "day", date.toString(), "REJECTED",
                    currentVersion, "NO_CHANGES", message));
            return fromStored(stored, null);
        }

        if (request.baseVersion() != currentVersion) {
            String message = "Запись дня изменилась на сервере";
            MobileSyncOperation stored = operations.save(new MobileSyncOperation(
                    user, operationId, "day", date.toString(), "CONFLICT",
                    currentVersion, "VERSION_CONFLICT", message));
            return fromStored(stored, null);
        }

        try {
            requireEnabledModules(user, change);
            DayDto saved = dayEntryService.patchMobileDayVersioned(user, change);
            Long serverVersion = saved.version();
            MobileSyncOperation stored = operations.save(new MobileSyncOperation(
                    user, operationId, "day", date.toString(), "APPLIED",
                    serverVersion, null, null));
            return fromStored(stored, saved);
        } catch (ApiException ex) {
            MobileSyncOperation stored = operations.save(new MobileSyncOperation(
                    user, operationId, "day", date.toString(), "REJECTED",
                    currentVersion, ex.getCode(), safeMessage(ex.getMessage())));
            return fromStored(stored, null);
        }
    }

    private boolean hasMutation(MobileDayChangeRequest change) {
        return change.shiftTypeId() != null
                || Boolean.TRUE.equals(change.clearShiftType())
                || change.note() != null
                || Boolean.TRUE.equals(change.clearNote())
                || change.dayEmoji() != null
                || Boolean.TRUE.equals(change.clearDayEmoji())
                || change.overtimeHours() != null
                || change.timeOffHours() != null;
    }

    private void requireEnabledModules(AppUser user, MobileDayChangeRequest change) {
        if (change.note() != null || Boolean.TRUE.equals(change.clearNote())) {
            moduleService.requireEnabled(user, ModuleService.NOTES);
        }
        if (change.overtimeHours() != null || change.timeOffHours() != null) {
            moduleService.requireEnabled(user, ModuleService.OVERTIME);
        }
    }

    private MobileSyncItemResultDto replay(MobileSyncOperation previous) {
        String status = "APPLIED".equals(previous.getStatus())
                ? "ALREADY_APPLIED"
                : previous.getStatus();
        return new MobileSyncItemResultDto(
                previous.getOperationId(),
                status,
                previous.getEntityType(),
                previous.getEntityKey(),
                previous.getServerVersion(),
                null,
                previous.getErrorCode(),
                previous.getMessage()
        );
    }

    private MobileSyncItemResultDto fromStored(MobileSyncOperation stored, DayDto entity) {
        return new MobileSyncItemResultDto(
                stored.getOperationId(),
                stored.getStatus(),
                stored.getEntityType(),
                stored.getEntityKey(),
                stored.getServerVersion(),
                entity,
                stored.getErrorCode(),
                stored.getMessage()
        );
    }

    private String safeEntityKey(String key) {
        if (key == null || key.isBlank()) return "unknown";
        String trimmed = key.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private String safeMessage(String message) {
        if (message == null) return null;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
