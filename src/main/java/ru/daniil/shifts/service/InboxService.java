package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.InboxConversionDto;
import ru.daniil.shifts.dto.Dtos.InboxCreateRequest;
import ru.daniil.shifts.dto.Dtos.InboxItemDto;
import ru.daniil.shifts.dto.Dtos.InboxToTaskRequest;
import ru.daniil.shifts.dto.Dtos.InboxUpdateRequest;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.InboxItem;
import ru.daniil.shifts.model.InboxItemStatus;
import ru.daniil.shifts.repo.InboxItemRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class InboxService {
    private final InboxItemRepository inbox;
    private final TaskService taskService;
    private final SecurityEventLogger securityEvents;

    public InboxService(InboxItemRepository inbox,
                        TaskService taskService,
                        SecurityEventLogger securityEvents) {
        this.inbox = inbox;
        this.taskService = taskService;
        this.securityEvents = securityEvents;
    }

    @Transactional(readOnly = true)
    public List<InboxItemDto> list(AppUser user, String status) {
        String normalized = status == null ? "open" : status.trim().toLowerCase(Locale.ROOT);
        List<InboxItem> items = switch (normalized) {
            case "open", "" -> inbox.findByOwnerAndStatusOrderByCreatedAtDescIdDesc(user, InboxItemStatus.OPEN);
            case "archived" -> inbox.findByOwnerAndStatusOrderByCreatedAtDescIdDesc(user, InboxItemStatus.ARCHIVED);
            case "all" -> inbox.findByOwnerOrderByCreatedAtDescIdDesc(user);
            default -> throw ApiException.badRequest("Неизвестный статус входящих");
        };
        return items.stream().map(InboxItemDto::from).toList();
    }

    @Transactional
    public InboxItemDto create(AppUser user, InboxCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        String text = cleanText(req.text());
        String operationId = cleanOperationId(req.clientOperationId());
        if (operationId != null) {
            var existing = inbox.findByOwnerAndClientOperationId(user, operationId);
            if (existing.isPresent()) return InboxItemDto.from(existing.get());
        }
        return InboxItemDto.from(inbox.save(new InboxItem(user, text, operationId)));
    }

    @Transactional
    public InboxItemDto update(AppUser user, Long id, InboxUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        InboxItem item = requireOwned(user, id);
        if (req.text() != null) item.setText(cleanText(req.text()));
        if (req.archived() != null) {
            item.setStatus(req.archived() ? InboxItemStatus.ARCHIVED : InboxItemStatus.OPEN);
            item.setResolvedAt(req.archived() ? LocalDateTime.now() : null);
        }
        return InboxItemDto.from(inbox.save(item));
    }

    @Transactional
    public InboxConversionDto convertToTask(AppUser user, Long id, InboxToTaskRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        InboxItem item = requireOwned(user, id);
        if (item.getStatus() != InboxItemStatus.OPEN) {
            throw ApiException.conflict("Запись уже разобрана");
        }
        TaskDto task = taskService.create(user, new TaskCreateRequest(
                req.date(),
                item.getText(),
                req.category(),
                req.tags(),
                req.priority(),
                req.dueDate(),
                req.dueTime(),
                req.reminderEnabled(),
                req.reminderMinutesBefore(),
                req.subtasks()
        ));
        item.setStatus(InboxItemStatus.ARCHIVED);
        item.setResolvedAt(LocalDateTime.now());
        InboxItemDto saved = InboxItemDto.from(inbox.save(item));
        return new InboxConversionDto(saved, task);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        inbox.delete(requireOwned(user, id));
    }

    private InboxItem requireOwned(AppUser user, Long id) {
        if (id == null) throw ApiException.badRequest("Не указан id записи");
        InboxItem item = inbox.findById(id).orElseThrow(() -> ApiException.notFound("Запись не найдена"));
        if (!Objects.equals(item.getOwner().getId(), user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=inbox id=" + id);
            throw ApiException.notFound("Запись не найдена");
        }
        return item;
    }

    private String cleanText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) throw ApiException.badRequest("Текст записи не должен быть пустым");
        if (text.length() > 2000) throw ApiException.badRequest("Текст записи: максимум 2000 символов");
        return text;
    }

    private String cleanOperationId(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isBlank()) return null;
        if (cleaned.length() > 80) throw ApiException.badRequest("Идентификатор операции слишком длинный");
        return cleaned;
    }
}
