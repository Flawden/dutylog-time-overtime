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
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.ShiftTypeService;

import static org.hamcrest.Matchers.hasItems;
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

/** HTTP contract for shift type CRUD, validation, built-in protection and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShiftTypeControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired ShiftTypeService shiftTypeService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("shift-type-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("shift-type-controller-other", "{noop}unused"));
    }

    @Test
    void fullCrudWorksAcrossLegacyAndV1Aliases() throws Exception {
        mvc.perform(get("/api/shift-types")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", hasItems("Дневная", "Ночная", "Выходной")));

        String createdBody = mvc.perform(post("/api/v1/shift-types")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Дежурная",
                                  "hours":12,
                                  "color":"#123456",
                                  "startTime":"07:00",
                                  "endTime":"19:00",
                                  "breakMinutes":45,
                                  "plannedHours":11.25,
                                  "notificationsEnabled":true,
                                  "notificationMinutesBefore":30
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.builtin").value(false))
                .andExpect(jsonPath("$.plannedHours").value(11.25))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createdBody);
        long id = created.path("id").asLong();

        mvc.perform(patch("/api/shift-types/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Аварийная",
                                  "hours":10,
                                  "color":"#ABCDEF",
                                  "startTime":"06:15",
                                  "endTime":"16:45",
                                  "breakMinutes":60,
                                  "plannedHours":9,
                                  "notificationsEnabled":false,
                                  "notificationMinutesBefore":-1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Аварийная"))
                .andExpect(jsonPath("$.startTime").value("06:15"))
                .andExpect(jsonPath("$.notificationMinutesBefore").value(nullValue()));

        mvc.perform(delete("/api/v1/shift-types/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/shift-types")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void validationFailuresUseStableFieldEnvelopes() throws Exception {
        mvc.perform(post("/api/shift-types")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"   ",
                                  "hours":25,
                                  "color":"red",
                                  "startTime":"25:61",
                                  "breakMinutes":1441,
                                  "plannedHours":-1,
                                  "notificationMinutesBefore":-2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.hours").exists())
                .andExpect(jsonPath("$.fields.color").exists())
                .andExpect(jsonPath("$.fields.startTime").exists())
                .andExpect(jsonPath("$.fields.breakMinutes").exists())
                .andExpect(jsonPath("$.fields.plannedHours").exists())
                .andExpect(jsonPath("$.fields.notificationMinutesBefore").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/shift-types")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void builtinNameColorAndDeletionReturnConflictButTimingRemainsEditable() throws Exception {
        long dayId = shiftTypeService.list(owner).stream()
                .filter(s -> "Дневная".equals(s.name())).findFirst().orElseThrow().id();

        mvc.perform(patch("/api/shift-types/{id}", dayId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"startTime\":\"09:00\",\"endTime\":\"18:00\",\"plannedHours\":8.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Дневная"))
                .andExpect(jsonPath("$.startTime").value("09:00"));

        mvc.perform(patch("/api/shift-types/{id}", dayId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Переименованная\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mvc.perform(delete("/api/shift-types/{id}", dayId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void foreignIdsRemainNotFoundForUpdateAndDelete() throws Exception {
        long foreignId = shiftTypeService.create(other, new ShiftTypeCreateRequest(
                "Чужая", 8.0, "#123456", null, null, null, null, null, null)).id();

        mvc.perform(patch("/api/shift-types/{id}", foreignId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Взлом\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/shift-types/{id}", foreignId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/shift-types")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"name\":\"Без CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/shift-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }
}
