package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.CalendarLayerEntryDto;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateApplyRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateApplyResultDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateCreateRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplatePreviewDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplatePreviewItemDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ScheduleTemplate;
import ru.daniil.shifts.model.ScheduleTemplateStep;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.CalendarLayerRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ScheduleTemplateRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ScheduleTemplateService {
    public static final int MAX_STEPS = 64;
    public static final int MAX_RANGE_DAYS = 366;

    private final ScheduleTemplateRepository templates;
    private final CalendarLayerRepository layers;
    private final DayEntryRepository days;
    private final ShiftTypeService shiftTypeService;
    private final ShiftOccurrenceService shiftOccurrenceService;
    private final DayEntryService dayEntryService;
    private final AccountingPeriodLockService periodLocks;
    private final SecurityEventLogger securityEvents;
    private final EntityManager entityManager;

    public ScheduleTemplateService(ScheduleTemplateRepository templates,
                                   CalendarLayerRepository layers,
                                   DayEntryRepository days,
                                   ShiftTypeService shiftTypeService,
                                   ShiftOccurrenceService shiftOccurrenceService,
                                   DayEntryService dayEntryService,
                                   AccountingPeriodLockService periodLocks,
                                   SecurityEventLogger securityEvents,
                                   EntityManager entityManager) {
        this.templates = templates;
        this.layers = layers;
        this.days = days;
        this.shiftTypeService = shiftTypeService;
        this.shiftOccurrenceService = shiftOccurrenceService;
        this.dayEntryService = dayEntryService;
        this.periodLocks = periodLocks;
        this.securityEvents = securityEvents;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<ScheduleTemplateDto> list(AppUser user) {
        ensureDefaults(user);
        return templates.findByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .map(ScheduleTemplateDto::from)
                .toList();
    }

    @Transactional
    public ScheduleTemplateDto create(AppUser user, ScheduleTemplateCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        ensureDefaults(user);
        ScheduleTemplate template = new ScheduleTemplate(user);
        template.setName(cleanName(req.name()));
        ensureUniqueName(user, template.getName(), null);
        template.setDescription(blankToNull(req.description()));
        template.setAlignmentMode(normalizeAlignment(req.alignmentMode()));
        template.setSystemPreset(false);
        template.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        List<ShiftType> steps = resolveSteps(user, req.shiftTypeIds());
        validateAlignment(template.getAlignmentMode(), steps.size());
        template.replaceSteps(steps);
        return ScheduleTemplateDto.from(templates.saveAndFlush(template));
    }

    @Transactional
    public ScheduleTemplateDto update(AppUser user, Long id, ScheduleTemplateUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        ScheduleTemplate template = requireOwned(user, id);
        if (template.isSystemPreset()) {
            throw ApiException.conflict("Встроенный шаблон нельзя изменять; создайте пользовательскую копию");
        }
        if (req.name() != null) {
            String name = cleanName(req.name());
            ensureUniqueName(user, name, template.getId());
            template.setName(name);
        }
        if (req.description() != null) template.setDescription(blankToNull(req.description()));
        String alignment = req.alignmentMode() == null
                ? template.getAlignmentMode()
                : normalizeAlignment(req.alignmentMode());
        List<ShiftType> steps = req.shiftTypeIds() == null
                ? template.getSteps().stream().map(ScheduleTemplateStep::getShiftType).toList()
                : resolveSteps(user, req.shiftTypeIds());
        validateAlignment(alignment, steps.size());
        template.setAlignmentMode(alignment);
        if (req.shiftTypeIds() != null) {
            // Flush orphan removals before inserting replacement positions. Hibernate executes
            // inserts before deletes in a single flush, which can otherwise violate the
            // unique (template_id, position) constraint while editing an existing cycle.
            template.getSteps().clear();
            templates.saveAndFlush(template);
            template.replaceSteps(steps);
        }
        if (req.sortOrder() != null) template.setSortOrder(req.sortOrder());
        return ScheduleTemplateDto.from(templates.saveAndFlush(template));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ScheduleTemplate template = requireOwned(user, id);
        if (template.isSystemPreset()) {
            throw ApiException.conflict("Встроенный шаблон нельзя удалить");
        }
        if (layers.existsByTemplate(template)) {
            throw ApiException.conflict("Шаблон используется календарным слоем");
        }
        templates.delete(template);
    }

    @Transactional(readOnly = true)
    public ScheduleTemplatePreviewDto preview(AppUser user, Long id, ScheduleTemplateApplyRequest req) {
        return buildPreview(user, requireOwned(user, id), req);
    }

    @Transactional
    public ScheduleTemplateApplyResultDto apply(AppUser user, Long id, ScheduleTemplateApplyRequest req) {
        ScheduleTemplate template = requireOwned(user, id);
        ParsedRange range = parseRange(req);
        ScheduleTemplatePreviewDto preview = buildPreview(user, template, req);

        Map<LocalDate, DayEntry> existing = new HashMap<>();
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, range.from(), range.to())) {
            existing.put(entry.getDate(), entry);
        }

        int applied = 0;
        for (ScheduleTemplatePreviewItemDto item : preview.items()) {
            if (!"APPLY".equals(item.action()) && !"OVERWRITE".equals(item.action())) continue;
            LocalDate date = LocalDate.parse(item.date());
            DayEntry entry = existing.getOrDefault(date, new DayEntry(user, date));
            ShiftType shift = shiftTypeService.requireOwnedShiftType(user, item.shiftTypeId());
            Long currentShiftId = entry.getShiftType() == null ? null : entry.getShiftType().getId();
            if (!java.util.Objects.equals(currentShiftId, shift.getId())) {
                periodLocks.assertOpen(user, date);
            }
            shiftOccurrenceService.assign(user, entry, shift, false);
            days.save(entry);
            existing.put(date, entry);
            applied++;
        }
        days.flush();
        entityManager.clear();

        List<DayDto> persisted = dayEntryService.listRange(user, range.from(), range.to());
        return new ScheduleTemplateApplyResultDto(
                template.getId(), range.from().toString(), range.to().toString(), applied,
                preview.unchangedCount(), preview.skippedCount(), preview.conflictCount(), persisted
        );
    }

    @Transactional
    public void ensureDefaults(AppUser user) {
        // First-run boot has two legitimate readers (bounded legacy boot + Vue Settings).
        // Serialize lazy preset seeding per owner in the database so concurrent transactions
        // cannot both observe an empty set and race the unique (user_id, name) constraint.
        AppUser lockedOwner = entityManager.find(AppUser.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (lockedOwner == null) throw ApiException.notFound("Пользователь не найден");

        Map<String, ShiftType> byName = new LinkedHashMap<>();
        for (var dto : shiftTypeService.list(lockedOwner)) {
            ShiftType shift = shiftTypeService.requireOwnedShiftType(lockedOwner, dto.id());
            byName.put(shift.getName(), shift);
        }
        ShiftType day = requireNamed(byName, "Дневная");
        ShiftType night = requireNamed(byName, "Ночная");
        ShiftType off = requireNamed(byName, "Выходной");

        addPreset(lockedOwner, "2 через 2", "Две дневные смены, затем два выходных.", "CYCLE_START",
                List.of(day, day, off, off), 10);
        addPreset(lockedOwner, "День / Ночь / 48", "Дневная, ночная и двое суток отдыха.", "CYCLE_START",
                List.of(day, night, off, off), 20);
        addPreset(lockedOwner, "Пятидневка", "Понедельник–пятница рабочие, суббота и воскресенье выходные.", "WEEKDAY",
                List.of(day, day, day, day, day, off, off), 30);
        addPreset(lockedOwner, "День / 72", "Одна дневная смена и трое суток отдыха.", "CYCLE_START",
                List.of(day, off, off, off), 40);
        addPreset(lockedOwner, "Ночь / 72", "Одна ночная смена и трое суток отдыха.", "CYCLE_START",
                List.of(night, off, off, off), 50);
        templates.flush();
    }

    public ScheduleTemplate requireOwned(AppUser user, Long id) {
        ScheduleTemplate template = templates.findById(id)
                .orElseThrow(() -> ApiException.notFound("Шаблон графика не найден"));
        if (!template.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=schedule_template id=" + id);
            throw ApiException.notFound("Шаблон графика не найден");
        }
        return template;
    }

    public ScheduleTemplateStep stepFor(ScheduleTemplate template, LocalDate date, LocalDate anchorDate) {
        if (template.getSteps().isEmpty()) throw ApiException.badRequest("Шаблон графика пуст");
        int size = template.getSteps().size();
        int position;
        if ("WEEKDAY".equals(template.getAlignmentMode())) {
            position = Math.floorMod(date.getDayOfWeek().getValue() - 1, size);
        } else {
            long distance = ChronoUnit.DAYS.between(anchorDate, date);
            position = (int) Math.floorMod(distance, (long) size);
        }
        return template.getSteps().get(position);
    }

    private ScheduleTemplatePreviewDto buildPreview(AppUser user,
                                                     ScheduleTemplate template,
                                                     ScheduleTemplateApplyRequest req) {
        ParsedRange range = parseRange(req);
        Map<LocalDate, DayEntry> existing = new HashMap<>();
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, range.from(), range.to())) {
            existing.put(entry.getDate(), entry);
        }

        List<ScheduleTemplatePreviewItemDto> items = new ArrayList<>();
        int writes = 0;
        int unchanged = 0;
        int skipped = 0;
        int conflicts = 0;
        for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
            ScheduleTemplateStep step = stepFor(template, date, range.anchor());
            ShiftType planned = step.getShiftType();
            DayEntry current = existing.get(date);
            ShiftType currentShift = current == null ? null : current.getShiftType();
            String action;
            if (currentShift == null) {
                action = "APPLY";
                writes++;
            } else if (currentShift.getId().equals(planned.getId())) {
                action = "SAME";
                unchanged++;
            } else if (range.overwrite()) {
                action = "OVERWRITE";
                writes++;
                conflicts++;
            } else {
                action = "SKIP_CONFLICT";
                skipped++;
                conflicts++;
            }
            items.add(new ScheduleTemplatePreviewItemDto(
                    date.toString(), step.getPosition(), planned.getId(), planned.getName(), planned.getColor(),
                    currentShift == null ? null : currentShift.getId(),
                    currentShift == null ? null : currentShift.getName(), action
            ));
        }
        return new ScheduleTemplatePreviewDto(
                template.getId(), template.getName(), range.from().toString(), range.to().toString(),
                range.anchor().toString(), range.overwrite(), items.size(), writes, unchanged, skipped, conflicts, items
        );
    }

    private ParsedRange parseRange(ScheduleTemplateApplyRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate from = dayEntryService.parseDate(req.startDate(), "Дата начала должна быть в формате yyyy-MM-dd");
        LocalDate to = dayEntryService.parseDate(req.endDate(), "Дата окончания должна быть в формате yyyy-MM-dd");
        dayEntryService.validateRange(from, to);
        LocalDate anchor = req.anchorDate() == null || req.anchorDate().isBlank()
                ? from
                : dayEntryService.parseDate(req.anchorDate(), "Опорная дата должна быть в формате yyyy-MM-dd");
        return new ParsedRange(from, to, anchor, Boolean.TRUE.equals(req.overwriteExistingShift()));
    }

    private List<ShiftType> resolveSteps(AppUser user, List<Long> shiftTypeIds) {
        if (shiftTypeIds == null || shiftTypeIds.isEmpty()) {
            throw ApiException.badRequest("Цикл должен содержать хотя бы один элемент");
        }
        if (shiftTypeIds.size() > MAX_STEPS) {
            throw ApiException.badRequest("Цикл не должен быть длиннее " + MAX_STEPS + " элементов");
        }
        return shiftTypeIds.stream().map(id -> shiftTypeService.requireOwnedShiftType(user, id)).toList();
    }

    private void validateAlignment(String alignment, int stepCount) {
        if ("WEEKDAY".equals(alignment) && stepCount != 7) {
            throw ApiException.badRequest("Шаблон с привязкой к дням недели должен содержать ровно 7 элементов");
        }
    }

    private void addPreset(AppUser user, String name, String description, String alignment,
                           List<ShiftType> steps, int sortOrder) {
        if (templates.findByOwnerAndName(user, name).isPresent()) return;
        ScheduleTemplate template = new ScheduleTemplate(user);
        template.setName(name);
        template.setDescription(description);
        template.setAlignmentMode(alignment);
        template.setSystemPreset(true);
        template.setSortOrder(sortOrder);
        template.replaceSteps(steps);
        templates.save(template);
    }

    private ShiftType requireNamed(Map<String, ShiftType> byName, String name) {
        ShiftType shift = byName.get(name);
        if (shift == null) throw new IllegalStateException("Missing built-in shift type: " + name);
        return shift;
    }

    private void ensureUniqueName(AppUser user, String name, Long currentId) {
        templates.findByOwnerAndName(user, name).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw ApiException.conflict("Шаблон с таким названием уже существует");
            }
        });
    }

    private String cleanName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw ApiException.badRequest("Название шаблона не должно быть пустым");
        return value;
    }

    private String blankToNull(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }

    private String normalizeAlignment(String raw) {
        String value = raw == null || raw.isBlank() ? "CYCLE_START" : raw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CYCLE_START", "WEEKDAY").contains(value)) {
            throw ApiException.badRequest("alignmentMode: CYCLE_START или WEEKDAY");
        }
        return value;
    }

    private record ParsedRange(LocalDate from, LocalDate to, LocalDate anchor, boolean overwrite) {}
}
