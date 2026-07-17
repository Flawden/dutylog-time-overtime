package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.ImportantDayService;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract tests for important dates, recurrences, modules and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImportantDayControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ImportantDayService importantDayService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("important-day-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("important-day-controller-other", "{noop}unused"));
    }

    @Test
    void fullCrudWorksAcrossLegacyAndV1Aliases() throws Exception {
        setImportantDatesEnabled(owner, true);

        String body = mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"  День проекта  ","date":"2026-08-31","repeatMode":"MONTHLY","color":"#A1B2C3"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("День проекта"))
                .andExpect(jsonPath("$.repeatMode").value("MONTHLY"))
                .andReturn().getResponse().getContentAsString();

        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();

        mvc.perform(get("/api/v1/important-days")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(get("/api/important-days/occurrences")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-08-31"))
                .andExpect(jsonPath("$[1].date").value("2026-09-30"));

        mvc.perform(patch("/api/v1/important-days/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Годовщина","date":"2024-02-29","repeatMode":"YEARLY","color":"#FEDCBA"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Годовщина"))
                .andExpect(jsonPath("$.date").value("2024-02-29"))
                .andExpect(jsonPath("$.repeatMode").value("YEARLY"));

        mvc.perform(delete("/api/important-days/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validationAndMalformedRangesUseStableErrorEnvelopes() throws Exception {
        setImportantDatesEnabled(owner, true);

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"   \",\"date\":\"2026-08-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Событие\",\"date\":\"2026-08-10\",\"color\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.color").exists());

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Событие\",\"date\":\"10.08.2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Событие\",\"date\":\"2026-08-10\",\"repeatMode\":\"WEEKLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        mvc.perform(get("/api/important-days/occurrences")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("to", "2026-08-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.fields.from").value("required"));

        mvc.perform(get("/api/important-days/occurrences")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-31")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void disabledModuleGuardsEveryEndpointWithoutDeletingStoredDates() throws Exception {
        var existing = importantDayService.create(owner,
                new ImportantDayCreateRequest("Сохранённая дата", "2026-08-10", RepeatMode.NONE, "#123456"));
        setImportantDatesEnabled(owner, false);

        mvc.perform(get("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:important_dates"));

        mvc.perform(get("/api/important-days/occurrences")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Новая\",\"date\":\"2026-08-11\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/important-days/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Изменение\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/important-days/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        setImportantDatesEnabled(owner, true);
        mvc.perform(get("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(existing.id()))
                .andExpect(jsonPath("$[0].title").value("Сохранённая дата"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        setImportantDatesEnabled(owner, true);

        mvc.perform(post("/api/important-days")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"title\":\"Без CSRF\",\"date\":\"2026-08-10\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/important-days"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void foreignIdsAreIndistinguishableFromMissingResources() throws Exception {
        setImportantDatesEnabled(owner, true);
        setImportantDatesEnabled(other, true);
        var foreign = importantDayService.create(other,
                new ImportantDayCreateRequest("Чужое событие", "2026-08-10", RepeatMode.NONE, "#123456"));

        mvc.perform(patch("/api/important-days/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"title\":\"Взломано\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/important-days/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void occurrencesRemainOwnerScopedAndSortedByDateThenTitle() throws Exception {
        setImportantDatesEnabled(owner, true);
        setImportantDatesEnabled(other, true);
        var beta = importantDayService.create(owner,
                new ImportantDayCreateRequest("Бета", "2026-08-10", RepeatMode.NONE, "#111111"));
        var alpha = importantDayService.create(owner,
                new ImportantDayCreateRequest("Альфа", "2026-08-10", RepeatMode.NONE, "#222222"));
        importantDayService.create(other,
                new ImportantDayCreateRequest("Чужое", "2026-08-09", RepeatMode.NONE, "#333333"));

        mvc.perform(get("/api/v1/important-days/occurrences")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(alpha.id()))
                .andExpect(jsonPath("$[1].id").value(beta.id()))
                .andExpect(jsonPath("$[*].title", hasItem("Альфа")))
                .andExpect(jsonPath("$[*].title", hasItem("Бета")));
    }

    private void setImportantDatesEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"important_dates\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
