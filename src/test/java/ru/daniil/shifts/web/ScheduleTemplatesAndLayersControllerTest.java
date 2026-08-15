package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateCreateRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.ScheduleTemplateService;
import ru.daniil.shifts.service.ShiftTypeService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleTemplatesAndLayersControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeService shiftTypes;
    @Autowired ScheduleTemplateService templates;
    @Autowired ObjectMapper objectMapper;

    AppUser owner;
    AppUser other;
    Map<String, Long> shifts;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("schedule-layer-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("schedule-layer-controller-other", "{noop}unused"));
        shifts = shiftTypes.list(owner).stream().collect(Collectors.toMap(s -> s.name(), s -> s.id()));
    }

    @Test
    void templateCrudPreviewAndSafeApplyAreAvailableUnderV1() throws Exception {
        mvc.perform(get("/api/v1/schedule-templates")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].systemPreset").value(true));

        String response = mvc.perform(post("/api/v1/schedule-templates")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Мой цикл","alignmentMode":"CYCLE_START","shiftTypeIds":[%d,%d]}
                                """.formatted(shifts.get("Дневная"), shifts.get("Выходной"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Мой цикл"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("id").asLong();

        mvc.perform(patch("/api/v1/schedule-templates/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"description":"Обновлён","alignmentMode":"CYCLE_START","shiftTypeIds":[%d,%d,%d]}
                                """.formatted(shifts.get("Выходной"), shifts.get("Дневная"), shifts.get("Выходной"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Обновлён"))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].shiftTypeName").value("Выходной"))
                .andExpect(jsonPath("$.steps[1].shiftTypeName").value("Дневная"));

        mvc.perform(post("/api/v1/schedule-templates/{id}/preview", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","endDate":"2026-08-04","anchorDate":"2026-08-01","overwriteExistingShift":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writeCount").value(4))
                .andExpect(jsonPath("$.items[0].action").value("APPLY"));

        mvc.perform(post("/api/v1/schedule-templates/{id}/apply", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","endDate":"2026-08-04","anchorDate":"2026-08-01","overwriteExistingShift":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(4))
                .andExpect(jsonPath("$.days.length()").value(4));
    }

    @Test
    void layerCrudFeedsProjectedEntriesIntoCalendarAndVisibilityIsServerOwned() throws Exception {
        ScheduleTemplateDto template = templates.list(owner).stream()
                .filter(t -> "День / Ночь / 48".equals(t.name())).findFirst().orElseThrow();

        String response = mvc.perform(post("/api/calendar-layers")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Рома","color":"#55AAFF","timezone":"Europe/Berlin","visible":true,
                                  "templateId":%d,"anchorDate":"2026-08-01","startDate":"2026-08-01","endDate":"2026-08-05"
                                }
                                """.formatted(template.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.scheduleEditable").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("id").asLong();

        mvc.perform(put("/api/v1/calendar-layers/{id}/overrides/{date}", id, "2026-08-01")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"OFF\",\"reason\":\"TIME_OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("OFF"))
                .andExpect(jsonPath("$.reason").value("TIME_OFF"));

        mvc.perform(get("/api/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01").param("to", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarLayers.length()").value(1))
                .andExpect(jsonPath("$.calendarLayers[0].entries.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.calendarLayers[0].entries[0].layerName").value("Рома"))
                .andExpect(jsonPath("$.calendarLayers[0].entries[0].overrideKind").value("OFF"))
                .andExpect(jsonPath("$.calendarLayers[0].entries[0].overrideReason").value("TIME_OFF"));

        mvc.perform(delete("/api/v1/calendar-layers/{id}/overrides/{date}", id, "2026-08-01")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/v1/calendar-layers/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));

        mvc.perform(get("/api/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01").param("to", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarLayers[0].entries.length()").value(0));

        mvc.perform(delete("/api/calendar-layers/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void writesRequireAuthenticationCsrfAndOwnedResources() throws Exception {
        String payload = """
                {"name":"Без защиты","alignmentMode":"CYCLE_START","shiftTypeIds":[%d]}
                """.formatted(shifts.get("Дневная"));
        mvc.perform(post("/api/schedule-templates")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/schedule-templates")
                        .with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());

        ScheduleTemplateDto foreign = templates.list(other).get(0);
        mvc.perform(post("/api/schedule-templates/{id}/preview", foreign.id())
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-02\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationRejectsEmptyCyclesWeekdayLengthUnknownZonesAndReversedBounds() throws Exception {
        mvc.perform(post("/api/schedule-templates")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Пустой\",\"alignmentMode\":\"CYCLE_START\",\"shiftTypeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/schedule-templates")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Неделя","alignmentMode":"WEEKDAY","shiftTypeIds":[%d,%d]}
                                """.formatted(shifts.get("Дневная"), shifts.get("Выходной"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        ScheduleTemplateDto template = templates.list(owner).get(0);
        mvc.perform(post("/api/calendar-layers")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Ошибка","timezone":"Mars/Olympus","templateId":%d,"anchorDate":"2026-08-01","startDate":"2026-08-05","endDate":"2026-08-01"}
                                """.formatted(template.id())))
                .andExpect(status().isBadRequest());
    }
}
