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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression for v27.2.6: disabled optional modules must not break core day saves. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DayModuleIsolationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeService shiftTypes;

    private AppUser regular;
    private Long dayId;
    private Long nightId;

    @BeforeEach
    void setUp() throws Exception {
        regular = users.save(new AppUser("day-module-isolation", "unused-test-password"));
        Map<String, Long> ids = shiftTypes.list(regular).stream()
                .collect(Collectors.toMap(s -> s.name(), s -> s.id()));
        dayId = ids.get("Дневная");
        nightId = ids.get("Ночная");
        setModules(true, true);
    }

    @Test
    void neutralLegacyFieldsDoNotBlockShiftAndMarkerOrEraseHiddenData() throws Exception {
        mvc.perform(put("/api/days/2026-07-10")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "shiftTypeId":%d,
                                  "note":"keep this note",
                                  "dayEmoji":"🧪",
                                  "overtimeHours":2.5,
                                  "timeOffHours":1.0
                                }
                                """.formatted(dayId)))
                .andExpect(status().isOk());

        setModules(false, false);

        // v27.2.5 web clients always sent note:null and overtime/timeOff:0 even when
        // those modules were disabled. This must be accepted as a core-only update.
        mvc.perform(put("/api/days/2026-07-10")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "shiftTypeId":%d,
                                  "note":null,
                                  "dayEmoji":"✅",
                                  "overtimeHours":0,
                                  "timeOffHours":0
                                }
                                """.formatted(nightId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftTypeId").value(nightId))
                .andExpect(jsonPath("$.dayEmoji").value("✅"))
                .andExpect(jsonPath("$.overtimeHours").value(0))
                .andExpect(jsonPath("$.timeOffHours").value(0));

        mvc.perform(get("/api/days")
                        .with(user(regular.getUsername()).roles("USER"))
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].note").value(nullValue()))
                .andExpect(jsonPath("$[0].overtimeHours").value(0))
                .andExpect(jsonPath("$[0].timeOffHours").value(0));

        setModules(true, true);

        mvc.perform(get("/api/calendar")
                        .with(user(regular.getUsername()).roles("USER"))
                        .param("from", "2026-07-10")
                        .param("to", "2026-07-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].shiftTypeId").value(nightId))
                .andExpect(jsonPath("$.days[0].dayEmoji").value("✅"))
                .andExpect(jsonPath("$.days[0].note").value("keep this note"))
                .andExpect(jsonPath("$.days[0].overtimeHours").value(2.5))
                .andExpect(jsonPath("$.days[0].timeOffHours").value(1.0));
    }

    @Test
    void disabledModulesStillRejectRealWrites() throws Exception {
        setModules(false, false);

        mvc.perform(put("/api/days/2026-07-11")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"shiftTypeId\":" + dayId + ",\"note\":\"blocked\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/days/2026-07-11")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"shiftTypeId\":" + dayId + ",\"overtimeHours\":2}"))
                .andExpect(status().isForbidden());
    }

    private void setModules(boolean notes, boolean overtime) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"notes\":" + notes + ",\"overtime\":" + overtime + "}}"))
                .andExpect(status().isOk());
    }
}
