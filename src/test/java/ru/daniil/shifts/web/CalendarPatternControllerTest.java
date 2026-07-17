package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.ShiftTypeService;

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP coverage for bulk schedule patterns, validation and security boundaries. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarPatternControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeService shiftTypes;

    AppUser owner;
    AppUser other;
    Long dayId;
    Long nightId;
    Long offId;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("calendar-pattern-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("calendar-pattern-controller-other", "{noop}unused"));
        Map<String, Long> ids = shiftTypes.list(owner).stream()
                .collect(Collectors.toMap(s -> s.name(), s -> s.id()));
        dayId = ids.get("Дневная");
        nightId = ids.get("Ночная");
        offId = ids.get("Выходной");
    }

    @Test
    void v1FillAndCalendarReadPreserveDayNightFortyEightAcrossLeapDay() throws Exception {
        mvc.perform(post("/api/v1/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "startDate":"2028-02-27",
                                  "days":6,
                                  "shiftTypeIds":[%d,%d,%d,%d],
                                  "overwriteExistingShift":true
                                }
                                """.formatted(dayId, nightId, offId, offId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].date").value("2028-02-27"))
                .andExpect(jsonPath("$[1].shiftTypeId").value(nightId))
                .andExpect(jsonPath("$[2].date").value("2028-02-29"))
                .andExpect(jsonPath("$[2].shiftTypeId").value(offId))
                .andExpect(jsonPath("$[4].shiftTypeId").value(dayId));

        mvc.perform(get("/api/v1/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2028-02-27")
                        .param("to", "2028-03-03"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.days.length()").value(6))
                .andExpect(jsonPath("$.days[2].date").value("2028-02-29"))
                .andExpect(jsonPath("$.days[5].date").value("2028-03-03"));
    }

    @Test
    void invalidFillPayloadsUseStableValidationEnvelopes() throws Exception {
        mvc.perform(post("/api/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","days":0,"shiftTypeIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.days").exists())
                .andExpect(jsonPath("$.fields.shiftTypeIds").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"01.08.2026","days":3,"shiftTypeIds":[%d]}
                                """.formatted(dayId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","days":367,"shiftTypeIds":[%d]}
                                """.formatted(dayId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.days").exists());
    }

    @Test
    void foreignShiftIdsAreNotFoundAndDoNotPartiallyWriteTheSchedule() throws Exception {
        Long foreignId = shiftTypes.list(other).stream()
                .filter(s -> "Дневная".equals(s.name()))
                .findFirst().orElseThrow().id();

        mvc.perform(post("/api/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "startDate":"2026-09-01",
                                  "days":4,
                                  "shiftTypeIds":[%d,%d],
                                  "overwriteExistingShift":true
                                }
                                """.formatted(dayId, foreignId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(get("/api/calendar")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    void fillRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(post("/api/days/fill")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","days":1,"shiftTypeIds":[%d]}
                                """.formatted(dayId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/days/fill")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"startDate":"2026-08-01","days":1,"shiftTypeIds":[%d]}
                                """.formatted(dayId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }
}
