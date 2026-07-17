package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.QuickScenarioCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.QuickScenarioService;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for quick-scenario CRUD, validation, module guards and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuickScenarioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired QuickScenarioService quickScenarioService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("quick-scenario-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("quick-scenario-controller-other", "{noop}unused"));
    }

    @Test
    void fullCrudWorksAcrossLegacyAndV1Aliases() throws Exception {
        setScenariosEnabled(owner, true);

        mvc.perform(get("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("+2 часа"));

        String createdBody = mvc.perform(post("/api/v1/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Ночной ППР",
                                  "groupLabel":"ночь",
                                  "description":"Проверочный сценарий",
                                  "startMode":"SHIFT_START",
                                  "endMode":"FIXED_TIME",
                                  "endOffsetMinutes":0,
                                  "endFixedTime":"08:00",
                                  "endNextDay":true,
                                  "breakMode":"CUSTOM",
                                  "customBreakMinutes":45,
                                  "plannedMode":"CUSTOM",
                                  "customPlannedHours":8.5,
                                  "reasonTemplate":"ППР",
                                  "sortOrder":5
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ночной ППР"))
                .andExpect(jsonPath("$.endFixedTime").value("08:00"))
                .andExpect(jsonPath("$.endNextDay").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody);
        long id = created.path("id").asLong();

        mvc.perform(patch("/api/quick-scenarios/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"После смены +90",
                                  "endMode":"ADD_MINUTES",
                                  "endOffsetMinutes":90,
                                  "endFixedTime":"",
                                  "endNextDay":false,
                                  "groupLabel":"",
                                  "reasonTemplate":""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("После смены +90"))
                .andExpect(jsonPath("$.endMode").value("ADD_MINUTES"))
                .andExpect(jsonPath("$.endOffsetMinutes").value(90))
                .andExpect(jsonPath("$.endFixedTime").value(nullValue()))
                .andExpect(jsonPath("$.groupLabel").value(nullValue()));

        mvc.perform(delete("/api/v1/quick-scenarios/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void beanValidationAndConsistencyFailuresUseStableEnvelopes() throws Exception {
        setScenariosEnabled(owner, true);

        mvc.perform(post("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"   ",
                                  "startMode":"NOW",
                                  "endMode":"LATER",
                                  "endOffsetMinutes":-1,
                                  "endFixedTime":"25:00",
                                  "breakMode":"AUTO",
                                  "customBreakMinutes":1441,
                                  "plannedMode":"AUTO",
                                  "customPlannedHours":101,
                                  "sortOrder":-1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.startMode").exists())
                .andExpect(jsonPath("$.fields.endMode").exists())
                .andExpect(jsonPath("$.fields.endOffsetMinutes").exists())
                .andExpect(jsonPath("$.fields.endFixedTime").exists())
                .andExpect(jsonPath("$.fields.breakMode").exists())
                .andExpect(jsonPath("$.fields.customBreakMinutes").exists())
                .andExpect(jsonPath("$.fields.plannedMode").exists())
                .andExpect(jsonPath("$.fields.customPlannedHours").exists())
                .andExpect(jsonPath("$.fields.sortOrder").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/v1/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Без времени\",\"endMode\":\"FIXED_TIME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void disabledModuleGuardsEveryEndpointWithoutDeletingStoredScenarios() throws Exception {
        setScenariosEnabled(owner, true);
        var existing = quickScenarioService.create(owner, scenario("Сохранённый"));
        setScenariosEnabled(owner, false);

        mvc.perform(get("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.moduleKey").value("scenarios"));

        mvc.perform(post("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Новый\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/quick-scenarios/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Изменённый\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/quick-scenarios/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        setScenariosEnabled(owner, true);
        mvc.perform(get("/api/v1/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(existing.id()))
                .andExpect(jsonPath("$[0].name").value("Сохранённый"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        setScenariosEnabled(owner, true);

        mvc.perform(post("/api/quick-scenarios")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Без CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/quick-scenarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void foreignIdsAreIndistinguishableFromMissingResources() throws Exception {
        setScenariosEnabled(owner, true);
        setScenariosEnabled(other, true);
        var foreign = quickScenarioService.create(other, scenario("Чужой"));

        mvc.perform(patch("/api/quick-scenarios/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Взлом\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/quick-scenarios/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private QuickScenarioCreateRequest scenario(String name) {
        return new QuickScenarioCreateRequest(
                name, null, null,
                "SHIFT_END", "ADD_MINUTES", 120, null, false,
                "ZERO", 0, "ZERO", 0.0,
                null, 100);
    }

    private void setScenariosEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"scenarios\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
