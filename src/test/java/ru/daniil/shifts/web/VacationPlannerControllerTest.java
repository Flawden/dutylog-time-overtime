package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VacationPlannerControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("vacation-controller-owner", "{noop}unused"));
    }

    @Test
    void plannerAndDefaultsAreAvailableUnderV1WithoutCaching() throws Exception {
        mvc.perform(get("/api/v1/vacation-planner")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("referenceDate", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.settings.annualAllowanceDays").value(28))
                .andExpect(jsonPath("$.durationPresets[0]").value(14))
                .andExpect(jsonPath("$.types.length()").value(5))
                .andExpect(jsonPath("$.summary.remainingDays").value(28))
                .andExpect(jsonPath("$.settings.timeOffBalanceHours").value(0.0));
    }

    @Test
    void previewCreateUpdateCalendarProjectionAndDeleteFormOneFlow() throws Exception {
        String planner = mvc.perform(get("/api/vacation-planner")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("referenceDate", "2026-08-01"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long typeId = objectMapper.readTree(planner).path("types").get(0).path("id").asLong();

        mvc.perform(post("/api/v1/vacation-planner/preview")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"typeId":%d,"startDate":"2026-08-01","endDate":"2026-08-14"}
                                """.formatted(typeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarDays").value(14))
                .andExpect(jsonPath("$.remainingAfter").value(14));

        String created = mvc.perform(post("/api/v1/vacation-planner/absences")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"typeId":%d,"title":"Основной отпуск","startDate":"2026-08-01","endDate":"2026-08-14","status":"PLANNED"}
                                """.formatted(typeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countedDays").value(14))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(created).path("id").asLong();

        mvc.perform(patch("/api/vacation-planner/absences/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content("{\"status\":\"APPROVED\",\"endDate\":\"2026-08-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.countedDays").value(10));

        mvc.perform(get("/api/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01").param("to", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absences.length()").value(10))
                .andExpect(jsonPath("$.absences[0].title").value("Основной отпуск"));

        mvc.perform(delete("/api/v1/vacation-planner/absences/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void settingsAndCustomTypesAreValidatedAndPersisted() throws Exception {
        mvc.perform(patch("/api/vacation-planner/settings")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"annualAllowanceDays\":35,\"carryoverDays\":5,\"countMode\":\"WEEKDAYS\",\"workYearStartMonth\":7,\"workYearStartDay\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualAllowanceDays").value(35))
                .andExpect(jsonPath("$.countMode").value("WEEKDAYS"));

        String custom = mvc.perform(post("/api/vacation-planner/types")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Учёба\",\"color\":\"#112233\",\"countsAgainstAllowance\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemPreset").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(custom).path("id").asLong();

        mvc.perform(delete("/api/vacation-planner/types/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void partialTimeOffLifecycleUsesIndependentHourBalance() throws Exception {
        mvc.perform(patch("/api/vacation-planner/settings")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"timeOffBalanceHours\":8,\"defaultTimeOffDayHours\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeOffBalanceHours").value(8.0));

        String plannerJson = mvc.perform(get("/api/vacation-planner")
                        .with(user(owner.getUsername()).roles("USER")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long typeId = -1L;
        for (var type : objectMapper.readTree(plannerJson).path("types")) {
            if ("TIME_OFF".equals(type.path("systemCode").asText())) {
                typeId = type.path("id").asLong();
                break;
            }
        }
        if (typeId < 0) throw new IllegalStateException("TIME_OFF preset missing");

        String created = mvc.perform(post("/api/vacation-planner/absences")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"typeId":%d,"title":"Врач","startDate":"2026-08-06","endDate":"2026-08-06",
                                 "coverage":"PARTIAL","startTime":"09:00","endTime":"13:00","status":"APPROVED"}
                                """.formatted(typeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverage").value("PARTIAL"))
                .andExpect(jsonPath("$.chargedMinutes").value(240))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(created).path("id").asLong();

        mvc.perform(get("/api/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-06").param("to", "2026-08-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absences[0].coverage").value("PARTIAL"))
                .andExpect(jsonPath("$.absences[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.absences[0].endTime").value("13:00"));

        mvc.perform(delete("/api/vacation-planner/absences/{id}", id)
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void writesRequireCsrfAndAuthentication() throws Exception {
        mvc.perform(post("/api/vacation-planner/preview")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"typeId\":1,\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-02\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/vacation-planner"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRangeAndAllowanceOverflowReturnStableErrors() throws Exception {
        String planner = mvc.perform(get("/api/vacation-planner")
                        .with(user(owner.getUsername()).roles("USER")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long typeId = objectMapper.readTree(planner).path("types").get(0).path("id").asLong();

        mvc.perform(post("/api/vacation-planner/absences")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"typeId":%d,"startDate":"2026-08-02","endDate":"2026-08-01"}
                                """.formatted(typeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/vacation-planner/absences")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"typeId":%d,"startDate":"2026-08-01","endDate":"2026-08-29"}
                                """.formatted(typeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VACATION_LIMIT_EXCEEDED"));
    }
}
