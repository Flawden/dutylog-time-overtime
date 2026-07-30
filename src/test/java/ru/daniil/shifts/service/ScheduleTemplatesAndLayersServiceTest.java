package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.CalendarLayerCreateRequest;
import ru.daniil.shifts.dto.Dtos.CalendarLayerDto;
import ru.daniil.shifts.dto.Dtos.CalendarLayerUpdateRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateApplyRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateCreateRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplatePreviewDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ScheduleTemplateRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ScheduleTemplatesAndLayersServiceTest {

    @Autowired ScheduleTemplateService templates;
    @Autowired CalendarLayerService layers;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired DayEntryRepository days;
    @Autowired ScheduleTemplateRepository templateRepository;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;
    Map<String, Long> shifts;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("schedule-layer-owner", "{noop}unused"));
        owner.setWorkTimezone("Europe/Berlin");
        owner.setDisplayTimezone("Europe/Berlin");
        users.save(owner);
        other = users.save(new AppUser("schedule-layer-other", "{noop}unused"));
        shifts = shiftTypes.list(owner).stream().collect(Collectors.toMap(s -> s.name(), s -> s.id()));
    }

    @Test
    void firstListSeedsFiveImmutablePresetsExactlyOnce() {
        List<ScheduleTemplateDto> first = templates.list(owner);
        List<ScheduleTemplateDto> second = templates.list(owner);

        assertEquals(5, first.size());
        assertEquals(5, second.size());
        assertEquals(List.of("2 через 2", "День / Ночь / 48", "Пятидневка", "День / 72", "Ночь / 72"),
                first.stream().map(ScheduleTemplateDto::name).toList());
        assertTrue(first.stream().allMatch(ScheduleTemplateDto::systemPreset));
        assertEquals(7, first.stream().filter(t -> "Пятидневка".equals(t.name())).findFirst().orElseThrow().steps().size());
        assertEquals(5, templateRepository.findByOwnerOrderBySortOrderAscIdAsc(owner).size());

        ApiException update = assertThrows(ApiException.class, () -> templates.update(owner, first.get(0).id(),
                new ru.daniil.shifts.dto.Dtos.ScheduleTemplateUpdateRequest("Изменён", null, null, null, null)));
        assertEquals(HttpStatus.CONFLICT, update.getStatus());
    }

    @Test
    void previewSkipsOccupiedDaysByDefaultAndApplyPreservesManualShift() {
        ScheduleTemplateDto custom = templates.create(owner, new ScheduleTemplateCreateRequest(
                "Мой 2/2", null, "CYCLE_START",
                List.of(shifts.get("Дневная"), shifts.get("Дневная"), shifts.get("Выходной"), shifts.get("Выходной")), 90));
        DayEntry occupied = new DayEntry(owner, LocalDate.parse("2026-08-02"));
        occupied.setShiftType(shiftTypes.requireOwnedShiftType(owner, shifts.get("Ночная")));
        days.saveAndFlush(occupied);

        ScheduleTemplatePreviewDto preview = templates.preview(owner, custom.id(),
                new ScheduleTemplateApplyRequest("2026-08-01", "2026-08-04", "2026-08-01", false));
        assertEquals(4, preview.totalDays());
        assertEquals(3, preview.writeCount());
        assertEquals(1, preview.skippedCount());
        assertEquals("SKIP_CONFLICT", preview.items().get(1).action());

        var result = templates.apply(owner, custom.id(),
                new ScheduleTemplateApplyRequest("2026-08-01", "2026-08-04", "2026-08-01", false));
        assertEquals(3, result.appliedCount());
        Map<String, Long> persisted = result.days().stream().collect(Collectors.toMap(d -> d.date(), d -> d.shiftTypeId()));
        assertEquals(shifts.get("Ночная"), persisted.get("2026-08-02"));
        assertEquals(shifts.get("Выходной"), persisted.get("2026-08-03"));
    }

    @Test
    void overwriteAndWeekdayAlignmentAreExplicitAndDeterministic() {
        ScheduleTemplateDto weekly = templates.list(owner).stream()
                .filter(t -> "Пятидневка".equals(t.name())).findFirst().orElseThrow();
        DayEntry saturday = new DayEntry(owner, LocalDate.parse("2026-08-01"));
        saturday.setShiftType(shiftTypes.requireOwnedShiftType(owner, shifts.get("Ночная")));
        days.saveAndFlush(saturday);

        ScheduleTemplatePreviewDto preview = templates.preview(owner, weekly.id(),
                new ScheduleTemplateApplyRequest("2026-08-01", "2026-08-03", "1900-01-01", true));
        assertEquals(List.of("OVERWRITE", "APPLY", "APPLY"), preview.items().stream().map(i -> i.action()).toList());
        assertEquals(List.of("Выходной", "Выходной", "Дневная"), preview.items().stream().map(i -> i.shiftTypeName()).toList());

        var result = templates.apply(owner, weekly.id(),
                new ScheduleTemplateApplyRequest("2026-08-01", "2026-08-03", "1900-01-01", true));
        assertEquals(3, result.appliedCount());
        assertEquals(shifts.get("Выходной"), result.days().get(0).shiftTypeId());
    }

    @Test
    void cycleMathHandlesFarAnchorsAndOwnershipRemainsIsolated() {
        ScheduleTemplateDto cycle = templates.list(owner).get(0);
        var template = templates.requireOwned(owner, cycle.id());
        LocalDate anchor = LocalDate.parse("-999999999-01-01");
        LocalDate target = LocalDate.parse("+999999998-12-28");
        List<Integer> positions = new java.util.ArrayList<>();
        for (int offset = 0; offset < 4; offset++) {
            positions.add(templates.stepFor(template, target.plusDays(offset), anchor).getPosition());
        }
        assertEquals(List.of(1, 2, 3, 0), positions);

        ScheduleTemplateDto foreign = templates.list(other).get(0);
        ApiException error = assertThrows(ApiException.class, () -> templates.preview(owner, foreign.id(),
                new ScheduleTemplateApplyRequest("2026-08-01", "2026-08-02", null, false)));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void visibleLayerProjectsTimedOccurrencesIntoDisplayTimezoneAndCanBeHidden() {
        ScheduleTemplateDto cycle = templates.list(owner).stream()
                .filter(t -> "День / Ночь / 48".equals(t.name())).findFirst().orElseThrow();
        CalendarLayerDto created = layers.create(owner, new CalendarLayerCreateRequest(
                "Маша", "#AA66FF", "Pacific/Kiritimati", true, 10, cycle.id(),
                "2026-08-01", "2026-08-01", "2026-08-04"));
        assertTrue(created.readOnly());

        CalendarLayerDto projected = layers.listForRange(owner, LocalDate.parse("2026-07-31"), LocalDate.parse("2026-08-04")).get(0);
        assertFalse(projected.entries().isEmpty());
        assertTrue(projected.entries().stream().anyMatch(e -> e.timed() && e.startInstant() != null && e.displayStart() != null));
        assertTrue(projected.entries().stream().allMatch(e -> "Pacific/Kiritimati".equals(e.sourceTimezone())));

        layers.update(owner, created.id(), new CalendarLayerUpdateRequest(
                null, null, null, false, null, null, null, null, null, false));
        assertTrue(layers.listForRange(owner, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-04")).get(0).entries().isEmpty());
    }

    @Test
    void layerValidationRejectsUnknownZonesInvalidBoundsAndForeignTemplates() {
        ScheduleTemplateDto own = templates.list(owner).get(0);
        assertBadRequest(() -> layers.create(owner, new CalendarLayerCreateRequest(
                "Плохая зона", null, "Mars/Olympus", true, null, own.id(),
                "2026-08-01", "2026-08-01", null)));
        assertBadRequest(() -> layers.create(owner, new CalendarLayerCreateRequest(
                "Плохие даты", null, "Europe/Berlin", true, null, own.id(),
                "2026-08-01", "2026-08-05", "2026-08-01")));

        ScheduleTemplateDto foreign = templates.list(other).get(0);
        ApiException foreignError = assertThrows(ApiException.class, () -> layers.create(owner,
                new CalendarLayerCreateRequest("Чужой", null, "Europe/Berlin", true, null, foreign.id(),
                        "2026-08-01", "2026-08-01", null)));
        assertEquals(HttpStatus.NOT_FOUND, foreignError.getStatus());
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
