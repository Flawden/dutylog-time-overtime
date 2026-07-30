package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.CalendarLayerCreateRequest;
import ru.daniil.shifts.dto.Dtos.CalendarLayerDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerEntryDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CalendarLayer;
import ru.daniil.shifts.model.ScheduleTemplate;
import ru.daniil.shifts.model.ScheduleTemplateStep;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.CalendarLayerRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CalendarLayerService {
    private final CalendarLayerRepository layers;
    private final ScheduleTemplateService templateService;
    private final UserTimeService userTimeService;
    private final DayEntryService dayEntryService;
    private final SecurityEventLogger securityEvents;

    public CalendarLayerService(CalendarLayerRepository layers,
                                ScheduleTemplateService templateService,
                                UserTimeService userTimeService,
                                DayEntryService dayEntryService,
                                SecurityEventLogger securityEvents) {
        this.layers = layers;
        this.templateService = templateService;
        this.userTimeService = userTimeService;
        this.dayEntryService = dayEntryService;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public List<CalendarLayerDto> list(AppUser user) {
        templateService.ensureDefaults(user);
        return layers.findByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .map(layer -> toDto(layer, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarLayerDto> listForRange(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        return layers.findByOwnerOrderBySortOrderAscIdAsc(user).stream()
                .map(layer -> toDto(layer, layer.isVisible() ? project(user, layer, from, to) : List.of()))
                .toList();
    }

    @Transactional
    public CalendarLayerDto create(AppUser user, CalendarLayerCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        CalendarLayer layer = new CalendarLayer(user);
        applyCreate(user, layer, req);
        return toDto(layers.saveAndFlush(layer), List.of());
    }

    @Transactional
    public CalendarLayerDto update(AppUser user, Long id, CalendarLayerUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        CalendarLayer layer = requireOwned(user, id);
        if (req.name() != null) {
            String name = cleanName(req.name());
            ensureUniqueName(user, name, layer.getId());
            layer.setName(name);
        }
        if (req.color() != null) layer.setColor(req.color().toUpperCase());
        if (req.timezone() != null && !req.timezone().isBlank()) layer.setTimezone(validateTimezone(req.timezone()));
        if (req.visible() != null) layer.setVisible(req.visible());
        if (req.sortOrder() != null) layer.setSortOrder(req.sortOrder());
        if (req.templateId() != null) layer.setTemplate(templateService.requireOwned(user, req.templateId()));
        if (req.anchorDate() != null) layer.setAnchorDate(parseDate(req.anchorDate(), "Опорная дата слоя"));
        if (req.startDate() != null) layer.setStartDate(parseDate(req.startDate(), "Дата начала слоя"));
        if (Boolean.TRUE.equals(req.clearEndDate())) layer.setEndDate(null);
        else if (req.endDate() != null) layer.setEndDate(parseOptionalDate(req.endDate(), "Дата окончания слоя"));
        validateBounds(layer.getStartDate(), layer.getEndDate());
        return toDto(layers.saveAndFlush(layer), List.of());
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        layers.delete(requireOwned(user, id));
    }

    public CalendarLayer requireOwned(AppUser user, Long id) {
        CalendarLayer layer = layers.findById(id)
                .orElseThrow(() -> ApiException.notFound("Календарный слой не найден"));
        if (!layer.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=calendar_layer id=" + id);
            throw ApiException.notFound("Календарный слой не найден");
        }
        return layer;
    }

    private void applyCreate(AppUser user, CalendarLayer layer, CalendarLayerCreateRequest req) {
        String name = cleanName(req.name());
        ensureUniqueName(user, name, null);
        layer.setName(name);
        layer.setColor(req.color() == null || req.color().isBlank() ? "#7AB8FF" : req.color().toUpperCase());
        layer.setTimezone(validateTimezone(req.timezone()));
        layer.setVisible(req.visible() == null || req.visible());
        layer.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        layer.setTemplate(templateService.requireOwned(user, req.templateId()));
        layer.setAnchorDate(parseDate(req.anchorDate(), "Опорная дата слоя"));
        layer.setStartDate(parseDate(req.startDate(), "Дата начала слоя"));
        layer.setEndDate(parseOptionalDate(req.endDate(), "Дата окончания слоя"));
        validateBounds(layer.getStartDate(), layer.getEndDate());
    }

    private List<CalendarLayerEntryDto> project(AppUser user, CalendarLayer layer, LocalDate from, LocalDate to) {
        LocalDate sourceFrom = max(layer.getStartDate(), from.minusDays(2));
        LocalDate sourceTo = min(layer.getEndDate(), to.plusDays(2));
        if (sourceTo != null && sourceTo.isBefore(sourceFrom)) return List.of();
        if (sourceTo == null) sourceTo = to.plusDays(2);

        ScheduleTemplate template = layer.getTemplate();
        ZoneId sourceZone = ZoneId.of(layer.getTimezone());
        ZoneId displayZone = userTimeService.displayZone(user);
        List<CalendarLayerEntryDto> entries = new ArrayList<>();
        for (LocalDate sourceDate = sourceFrom; !sourceDate.isAfter(sourceTo); sourceDate = sourceDate.plusDays(1)) {
            ScheduleTemplateStep step = templateService.stepFor(template, sourceDate, layer.getAnchorDate());
            ShiftType shift = step.getShiftType();
            boolean timed = shift.getStartTime() != null && shift.getEndTime() != null;
            boolean dayOff = shift.effectivePlannedHours() <= 0.0001;
            String startInstant = null;
            String endInstant = null;
            String displayStart = null;
            String displayEnd = null;
            LocalDate displayDate = sourceDate;
            if (timed) {
                ZonedDateTime sourceStart = userTimeService.resolveLocalDateTime(
                        LocalDateTime.of(sourceDate, shift.getStartTime()), sourceZone);
                LocalDate endDate = !shift.getEndTime().isAfter(shift.getStartTime())
                        ? sourceDate.plusDays(1)
                        : sourceDate;
                ZonedDateTime sourceEnd = userTimeService.resolveLocalDateTime(
                        LocalDateTime.of(endDate, shift.getEndTime()), sourceZone);
                ZonedDateTime projectedStart = sourceStart.toInstant().atZone(displayZone);
                ZonedDateTime projectedEnd = sourceEnd.toInstant().atZone(displayZone);
                startInstant = sourceStart.toInstant().toString();
                endInstant = sourceEnd.toInstant().toString();

                // A projected overnight shift is represented by one read-only segment per
                // display date. This keeps month chips and the hourly Day view truthful
                // without persisting duplicate companion occurrences.
                for (LocalDate segmentDate = projectedStart.toLocalDate();
                     !segmentDate.isAfter(projectedEnd.toLocalDate());
                     segmentDate = segmentDate.plusDays(1)) {
                    ZonedDateTime dayStart = segmentDate.atStartOfDay(displayZone);
                    ZonedDateTime dayEnd = segmentDate.plusDays(1).atStartOfDay(displayZone);
                    ZonedDateTime segmentStart = projectedStart.isAfter(dayStart) ? projectedStart : dayStart;
                    ZonedDateTime segmentEnd = projectedEnd.isBefore(dayEnd) ? projectedEnd : dayEnd;
                    if (!segmentStart.isBefore(segmentEnd)) continue;
                    if (segmentDate.isBefore(from) || segmentDate.isAfter(to)) continue;
                    entries.add(new CalendarLayerEntryDto(
                            layer.getId(), layer.getName(), layer.getColor(), sourceDate.toString(), segmentDate.toString(),
                            shift.getId(), shift.getName(), shift.getColor(), layer.getTimezone(), startInstant, endInstant,
                            segmentStart.toLocalDateTime().toString(), segmentEnd.toLocalDateTime().toString(), true, false
                    ));
                }
                continue;
            }
            if (displayDate.isBefore(from) || displayDate.isAfter(to)) continue;
            entries.add(new CalendarLayerEntryDto(
                    layer.getId(), layer.getName(), layer.getColor(), sourceDate.toString(), displayDate.toString(),
                    shift.getId(), shift.getName(), shift.getColor(), layer.getTimezone(), startInstant, endInstant,
                    displayStart, displayEnd, false, dayOff
            ));
        }
        entries.sort(Comparator.comparing(CalendarLayerEntryDto::date)
                .thenComparing(e -> e.displayStart() == null ? "" : e.displayStart())
                .thenComparing(CalendarLayerEntryDto::layerId));
        return entries;
    }

    private CalendarLayerDto toDto(CalendarLayer layer, List<CalendarLayerEntryDto> entries) {
        return new CalendarLayerDto(
                layer.getId(), layer.getName(), layer.getColor(), layer.getTimezone(), layer.isVisible(),
                layer.getSortOrder(), layer.getTemplate().getId(), layer.getTemplate().getName(),
                layer.getAnchorDate().toString(), layer.getStartDate().toString(),
                layer.getEndDate() == null ? null : layer.getEndDate().toString(), true, entries
        );
    }

    private void ensureUniqueName(AppUser user, String name, Long currentId) {
        layers.findByOwnerAndName(user, name).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw ApiException.conflict("Слой с таким названием уже существует");
            }
        });
    }

    private String cleanName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) throw ApiException.badRequest("Название слоя не должно быть пустым");
        return value;
    }

    private String validateTimezone(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            if (value.isBlank()) throw new DateTimeException("blank");
            return ZoneId.of(value).getId();
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Неизвестный IANA-часовой пояс слоя");
        }
    }

    private LocalDate parseDate(String raw, String label) {
        return dayEntryService.parseDate(raw, label + " должна быть в формате yyyy-MM-dd");
    }

    private LocalDate parseOptionalDate(String raw, String label) {
        if (raw == null || raw.isBlank()) return null;
        return parseDate(raw, label);
    }

    private void validateBounds(LocalDate start, LocalDate end) {
        if (start == null) throw ApiException.badRequest("Дата начала слоя обязательна");
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("Дата окончания слоя не может быть раньше даты начала");
        }
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate min(LocalDate nullable, LocalDate other) {
        if (nullable == null) return other;
        return nullable.isBefore(other) ? nullable : other;
    }
}
