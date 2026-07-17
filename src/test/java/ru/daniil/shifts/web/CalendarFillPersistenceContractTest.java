package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.ShiftTypeService;

import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end regression for bulk calendar persistence and the authoritative month reload. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarFillPersistenceContractTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeService shiftTypes;

    private Long dayId;
    private Long offId;

    @BeforeEach
    void setUp() {
        AppUser user = users.save(new AppUser("calendar-fill-user", "unused-test-password"));
        Map<String, Long> ids = shiftTypes.list(user).stream()
                .collect(Collectors.toMap(s -> s.name(), s -> s.id()));
        dayId = ids.get("Дневная");
        offId = ids.get("Выходной");
    }

    @Test
    @WithMockUser(username = "calendar-fill-user")
    void fillThenFreshCalendarReadReturnsEveryPersistedDate() throws Exception {
        mvc.perform(post("/api/days/fill").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "startDate":"2026-08-01",
                                  "days":31,
                                  "shiftTypeIds":[%d,%d,%d,%d,%d,%d,%d],
                                  "overwriteExistingShift":true
                                }
                                """.formatted(offId, offId, dayId, dayId, dayId, dayId, dayId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(31))
                .andExpect(jsonPath("$[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$[0].shiftTypeId").value(offId))
                .andExpect(jsonPath("$[2].date").value("2026-08-03"))
                .andExpect(jsonPath("$[2].shiftTypeId").value(dayId));

        mvc.perform(get("/api/calendar")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("_", "123456"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.days.length()").value(31))
                .andExpect(jsonPath("$.days[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.days[0].shiftTypeId").value(offId))
                .andExpect(jsonPath("$.days[2].date").value("2026-08-03"))
                .andExpect(jsonPath("$.days[2].shiftTypeId").value(dayId))
                .andExpect(jsonPath("$.days[30].date").value("2026-08-31"));
    }
}
