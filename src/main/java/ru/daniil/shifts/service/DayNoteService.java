package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayNoteCreateRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteDto;
import ru.daniil.shifts.dto.Dtos.DayNoteMoveRequest;
import ru.daniil.shifts.dto.Dtos.DayNoteUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.DayNote;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.DayNoteRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DayNoteService {
    private static final int MAX_NOTES_PER_DAY = 100;

    private final DayNoteRepository notes;
    private final DayEntryRepository days;

    public DayNoteService(DayNoteRepository notes, DayEntryRepository days) {
        this.notes = notes;
        this.days = days;
    }

    @Transactional(readOnly = true)
    public List<DayNoteDto> listDate(AppUser user, LocalDate date) {
        return notes.findByOwnerAndDateOrderByPinnedDescSortOrderAscCreatedAtAscIdAsc(user, date)
                .stream().map(DayNoteDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DayNoteDto> listRange(AppUser user, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw ApiException.badRequest("Некорректный диапазон заметок");
        }
        return notes.findByOwnerAndDateBetweenOrderByDateAscPinnedDescSortOrderAscCreatedAtAscIdAsc(user, from, to)
                .stream().map(DayNoteDto::from).toList();
    }

    @Transactional
    public DayNoteDto create(AppUser user, DayNoteCreateRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON заметки");
        LocalDate date = parseDate(request.date());
        List<DayNote> existing = ordered(user, date);
        if (existing.size() >= MAX_NOTES_PER_DAY) {
            throw ApiException.badRequest("На один день можно создать максимум " + MAX_NOTES_PER_DAY + " заметок");
        }

        boolean pinned = Boolean.TRUE.equals(request.pinned());
        int nextOrder = existing.stream()
                .filter(note -> note.isPinned() == pinned)
                .mapToInt(DayNote::getSortOrder)
                .max().orElse(-10) + 10;
        DayNote note = new DayNote(user, date, normalizeContent(request.content()));
        String title = normalizeTitle(request.title());
        if (title == null && note.getContent().isBlank()) title = "Новая заметка";
        note.setTitle(title);
        note.setPinned(pinned);
        note.setSortOrder(nextOrder);
        DayNote saved = notes.saveAndFlush(note);
        syncLegacyShadow(user, date);
        return DayNoteDto.from(saved);
    }

    @Transactional
    public DayNoteDto update(AppUser user, Long id, DayNoteUpdateRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON заметки");
        DayNote note = requireOwned(user, id);
        boolean pinChanged = request.pinned() != null && request.pinned() != note.isPinned();

        if (request.title() != null) note.setTitle(normalizeTitle(request.title()));
        if (request.content() != null) note.setContent(normalizeContent(request.content()));
        if (request.pinned() != null) note.setPinned(request.pinned());

        if (pinChanged) {
            int nextOrder = ordered(user, note.getDate()).stream()
                    .filter(candidate -> !candidate.getId().equals(note.getId()))
                    .filter(candidate -> candidate.isPinned() == note.isPinned())
                    .mapToInt(DayNote::getSortOrder)
                    .max().orElse(-10) + 10;
            note.setSortOrder(nextOrder);
        }

        notes.saveAndFlush(note);
        normalizeOrders(user, note.getDate());
        syncLegacyShadow(user, note.getDate());
        return DayNoteDto.from(requireOwned(user, id));
    }

    @Transactional
    public List<DayNoteDto> move(AppUser user, Long id, DayNoteMoveRequest request) {
        if (request == null || request.direction() == null) {
            throw ApiException.badRequest("Нужно направление UP или DOWN");
        }
        DayNote note = requireOwned(user, id);
        List<DayNote> group = ordered(user, note.getDate()).stream()
                .filter(candidate -> candidate.isPinned() == note.isPinned())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int index = -1;
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).getId().equals(note.getId())) { index = i; break; }
        }
        String direction = request.direction().trim().toUpperCase(Locale.ROOT);
        int target = "UP".equals(direction) ? index - 1 : "DOWN".equals(direction) ? index + 1 : -99;
        if (target == -99) throw ApiException.badRequest("Направление должно быть UP или DOWN");
        if (index >= 0 && target >= 0 && target < group.size()) {
            DayNote other = group.get(target);
            int currentOrder = note.getSortOrder();
            note.setSortOrder(other.getSortOrder());
            other.setSortOrder(currentOrder);
            notes.save(note);
            notes.save(other);
            notes.flush();
        }
        normalizeOrders(user, note.getDate());
        return listDate(user, note.getDate());
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        DayNote note = requireOwned(user, id);
        LocalDate date = note.getDate();
        notes.delete(note);
        notes.flush();
        normalizeOrders(user, date);
        syncLegacyShadow(user, date);
    }

    /**
     * Compatibility bridge for legacy web/mobile clients that still edit DayEntry.note.
     * Only the current primary note is changed; sibling notes are never deleted.
     */
    @Transactional
    public void syncPrimaryFromLegacy(AppUser user, LocalDate date, String content) {
        List<DayNote> ordered = ordered(user, date);
        String normalized = normalizeNullableContent(content);
        if (ordered.isEmpty()) {
            if (normalized == null) return;
            DayNote note = new DayNote(user, date, normalized);
            note.setSortOrder(0);
            notes.saveAndFlush(note);
        } else {
            DayNote primary = ordered.get(0);
            if (normalized == null) {
                notes.delete(primary);
                notes.flush();
            } else {
                primary.setContent(normalized);
                notes.saveAndFlush(primary);
            }
        }
        normalizeOrders(user, date);
        syncLegacyShadow(user, date);
    }

    private DayNote requireOwned(AppUser user, Long id) {
        if (id == null) throw ApiException.notFound("Заметка не найдена");
        return notes.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Заметка не найдена"));
    }

    private List<DayNote> ordered(AppUser user, LocalDate date) {
        return notes.findByOwnerAndDateOrderByPinnedDescSortOrderAscCreatedAtAscIdAsc(user, date);
    }

    private void normalizeOrders(AppUser user, LocalDate date) {
        List<DayNote> ordered = ordered(user, date);
        int pinnedOrder = 0;
        int regularOrder = 0;
        for (DayNote note : ordered) {
            int expected = note.isPinned() ? pinnedOrder : regularOrder;
            if (note.getSortOrder() != expected) note.setSortOrder(expected);
            if (note.isPinned()) pinnedOrder += 10; else regularOrder += 10;
        }
        notes.saveAll(ordered);
        notes.flush();
    }

    private void syncLegacyShadow(AppUser user, LocalDate date) {
        List<DayNote> ordered = ordered(user, date);
        String primary = ordered.isEmpty() ? null : legacyShadowText(ordered.get(0));
        DayEntry day = days.findByOwnerAndDate(user, date).orElse(null);
        if (day == null && !ordered.isEmpty()) day = new DayEntry(user, date);
        if (day == null) return;
        day.setNote(primary);
        if (day.isEmpty()) days.delete(day); else days.save(day);
        days.flush();
    }

    private String legacyShadowText(DayNote note) {
        if (note.getContent() != null && !note.getContent().isBlank()) return note.getContent();
        if (note.getTitle() != null && !note.getTitle().isBlank()) return "# " + note.getTitle();
        // Keep an otherwise blank independent note discoverable through calendar/day
        // payloads whose root collection is still driven by day_entries.
        return "# Без названия";
    }

    private LocalDate parseDate(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException | NullPointerException ex) {
            throw ApiException.badRequest("Дата заметки должна быть в формате yyyy-MM-dd");
        }
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    private String normalizeContent(String value) {
        return value == null ? "" : value;
    }

    private String normalizeNullableContent(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
