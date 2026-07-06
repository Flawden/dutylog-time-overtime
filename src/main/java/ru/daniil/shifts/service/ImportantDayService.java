package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.dto.Dtos.ImportantDayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantDay;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.repo.ImportantDayRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ImportantDayService {
    private final ImportantDayRepository importantDays;
    private final DayEntryService dayEntryService;

    public ImportantDayService(ImportantDayRepository importantDays, DayEntryService dayEntryService) {
        this.importantDays = importantDays;
        this.dayEntryService = dayEntryService;
    }

    @Transactional(readOnly = true)
    public List<ImportantDayDto> list(AppUser user) {
        return importantDays.findByOwnerOrderByDateAscIdAsc(user).stream()
                .map(ImportantDayDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ImportantDayOccurrenceDto> occurrences(AppUser user, LocalDate from, LocalDate to) {
        dayEntryService.validateRange(from, to);
        List<ImportantDayOccurrenceDto> result = new ArrayList<>();
        for (ImportantDay day : importantDays.findByOwnerOrderByDateAscIdAsc(user)) {
            addOccurrences(day, from, to, result);
        }
        result.sort(Comparator
                .comparing(ImportantDayOccurrenceDto::date)
                .thenComparing(ImportantDayOccurrenceDto::title));
        return result;
    }

    @Transactional
    public ImportantDayDto create(AppUser user, ImportantDayCreateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        LocalDate date = dayEntryService.parseDate(req.date(), "Дата важного дня должна быть в формате yyyy-MM-dd");
        RepeatMode mode = req.repeatMode() == null ? RepeatMode.NONE : req.repeatMode();
        String color = req.color() == null || req.color().isBlank() ? "#F5B841" : req.color();
        ImportantDay day = new ImportantDay(user, req.title().trim(), date, mode, color);
        return ImportantDayDto.from(importantDays.save(day));
    }

    @Transactional
    public ImportantDayDto update(AppUser user, Long id, ImportantDayUpdateRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }
        ImportantDay day = requireOwnedImportantDay(user, id);
        if (req.title() != null) {
            String title = req.title().trim();
            if (title.isBlank()) {
                throw ApiException.badRequest("Название важного дня не должно быть пустым");
            }
            day.setTitle(title);
        }
        if (req.date() != null) {
            day.setDate(dayEntryService.parseDate(req.date(), "Дата важного дня должна быть в формате yyyy-MM-dd"));
        }
        if (req.repeatMode() != null) {
            day.setRepeatMode(req.repeatMode());
        }
        if (req.color() != null) {
            day.setColor(req.color());
        }
        return ImportantDayDto.from(importantDays.save(day));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ImportantDay day = requireOwnedImportantDay(user, id);
        importantDays.delete(day);
    }

    private ImportantDay requireOwnedImportantDay(AppUser user, Long id) {
        if (id == null) {
            throw ApiException.badRequest("Не указан id важного дня");
        }
        ImportantDay day = importantDays.findById(id)
                .orElseThrow(() -> ApiException.notFound("Важный день не найден"));
        if (!Objects.equals(day.getOwner().getId(), user.getId())) {
            throw ApiException.notFound("Важный день не найден");
        }
        return day;
    }

    private void addOccurrences(ImportantDay day, LocalDate from, LocalDate to, List<ImportantDayOccurrenceDto> out) {
        RepeatMode mode = day.getRepeatMode();
        if (mode == RepeatMode.NONE) {
            if (!day.getDate().isBefore(from) && !day.getDate().isAfter(to)) {
                out.add(toOccurrence(day, day.getDate()));
            }
            return;
        }

        if (mode == RepeatMode.YEARLY) {
            for (int year = from.getYear(); year <= to.getYear(); year++) {
                LocalDate occurrence = yearlyOccurrence(day.getDate(), year);
                if (!occurrence.isBefore(from) && !occurrence.isAfter(to)) {
                    out.add(toOccurrence(day, occurrence));
                }
            }
            return;
        }

        if (mode == RepeatMode.MONTHLY) {
            YearMonth month = YearMonth.from(from);
            YearMonth end = YearMonth.from(to);
            while (!month.isAfter(end)) {
                LocalDate occurrence = monthlyOccurrence(day.getDate(), month);
                if (!occurrence.isBefore(from) && !occurrence.isAfter(to)) {
                    out.add(toOccurrence(day, occurrence));
                }
                month = month.plusMonths(1);
            }
        }
    }

    private ImportantDayOccurrenceDto toOccurrence(ImportantDay day, LocalDate occurrenceDate) {
        return new ImportantDayOccurrenceDto(
                day.getId(),
                occurrenceDate.toString(),
                day.getTitle(),
                day.getRepeatMode(),
                day.getColor()
        );
    }

    /** 29 февраля в не-високосный год показываем 28 февраля, чтобы событие не пропадало. */
    private LocalDate yearlyOccurrence(LocalDate base, int year) {
        try {
            return LocalDate.of(year, base.getMonth(), base.getDayOfMonth());
        } catch (DateTimeException ex) {
            return LocalDate.of(year, 2, 28);
        }
    }

    /** Если ежемесячное событие стоит на 31 число, в коротких месяцах показываем последний день месяца. */
    private LocalDate monthlyOccurrence(LocalDate base, YearMonth month) {
        int day = Math.min(base.getDayOfMonth(), month.lengthOfMonth());
        return month.atDay(day);
    }
}
