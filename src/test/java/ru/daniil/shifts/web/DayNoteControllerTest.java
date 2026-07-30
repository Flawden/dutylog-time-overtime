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
import ru.daniil.shifts.dto.Dtos.DayNoteCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.DayNoteService;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for independent daily notes, module guards and owner isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DayNoteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired DayNoteService dayNoteService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("day-note-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("day-note-controller-other", "{noop}unused"));
    }

    @Test
    void fullCrudWorksAcrossLegacyAndV1AliasesWithoutOverwritingSiblings() throws Exception {
        setNotesEnabled(owner, true);

        long firstId = create(owner, "/api/notes", "2026-09-10", "Первая", "alpha", false);
        long secondId = create(owner, "/api/v1/notes", "2026-09-10", "Вторая", "beta", false);

        mvc.perform(get("/api/v1/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[1].id").value(secondId));

        mvc.perform(patch("/api/notes/{id}", secondId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"title":"Вторая обновлена","content":"beta updated","pinned":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Вторая обновлена"))
                .andExpect(jsonPath("$.content").value("beta updated"))
                .andExpect(jsonPath("$.pinned").value(true));

        mvc.perform(get("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(firstId))
                .andExpect(jsonPath("$[1].content").value("alpha"));

        mvc.perform(get("/api/v1/notes/search")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("q", "updated")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[0].title").value("Вторая обновлена"));

        mvc.perform(delete("/api/v1/notes/{id}", firstId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(secondId));
    }

    @Test
    void moveReordersOnlyTheSelectedPinGroup() throws Exception {
        setNotesEnabled(owner, true);
        long firstId = create(owner, "/api/notes", "2026-09-11", "A", "a", false);
        long secondId = create(owner, "/api/notes", "2026-09-11", "B", "b", false);
        long pinnedId = create(owner, "/api/notes", "2026-09-11", "P", "p", true);

        mvc.perform(post("/api/v1/notes/{id}/move", secondId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(pinnedId))
                .andExpect(jsonPath("$[1].id").value(secondId))
                .andExpect(jsonPath("$[2].id").value(firstId));
    }

    @Test
    void validationAndMalformedRangesUseStableErrors() throws Exception {
        setNotesEnabled(owner, true);

        mvc.perform(post("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"10.09.2026\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-09-10\",\"title\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists());

        mvc.perform(get("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-09-30")
                        .param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(get("/api/notes")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(get("/api/notes/search")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void disabledModuleAndOwnershipMismatchDoNotExposeStoredNotes() throws Exception {
        setNotesEnabled(other, true);
        var secret = dayNoteService.create(other,
                new DayNoteCreateRequest("2026-09-12", "Secret", "hidden", false));
        setNotesEnabled(owner, false);

        mvc.perform(get("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-09-12"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.moduleKey").value("notes"));

        setNotesEnabled(owner, true);
        mvc.perform(patch("/api/notes/{id}", secret.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"hacked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        setNotesEnabled(owner, true);

        mvc.perform(post("/api/notes")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"date\":\"2026-09-13\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/notes").param("date", "2026-09-13"))
                .andExpect(status().isUnauthorized());
    }

    private long create(AppUser account, String path, String date, String title, String content, boolean pinned) throws Exception {
        String body = mvc.perform(post(path)
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new DayNoteCreateRequest(date, title, content, pinned))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/notes/\\d+")))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.path("id").asLong();
    }

    private void setNotesEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"notes\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
