package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.InboxCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.InboxService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InboxControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired InboxService inboxService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() throws Exception {
        owner = users.save(new AppUser("inbox-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("inbox-controller-other", "{noop}unused"));
        setTasksEnabled(owner, true);
        setTasksEnabled(other, true);
    }

    @Test
    void createListArchiveConvertAndDeleteWorkThroughVersionedApi() throws Exception {
        String body = mvc.perform(post("/api/v1/inbox")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"text\":\"  Подготовить отчёт  \",\"clientOperationId\":\"capture-http-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Подготовить отчёт"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.clientOperationId").value("capture-http-1"))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();

        mvc.perform(get("/api/inbox")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(post("/api/inbox/{id}/task", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-12\",\"category\":\"Работа\",\"tags\":[\"Документы\"],\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inboxItem.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.task.text").value("Подготовить отчёт"))
                .andExpect(jsonPath("$.task.category").value("работа"))
                .andExpect(jsonPath("$.task.tags[0]").value("документы"));

        mvc.perform(get("/api/inbox").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/inbox").with(user(owner.getUsername()).roles("USER")).param("status", "archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(delete("/api/inbox/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void idempotentCreateReturnsTheSameItemAndInvalidPayloadIsRejected() throws Exception {
        String request = "{\"text\":\"Мысль\",\"clientOperationId\":\"retry-1\"}";
        String first = mvc.perform(post("/api/inbox")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first).get("id").asLong();

        mvc.perform(post("/api/inbox")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(post("/api/inbox")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void moduleCsrfAuthenticationAndOwnershipBoundariesAreEnforced() throws Exception {
        var foreign = inboxService.create(other, new InboxCreateRequest("Чужая запись", null));

        mvc.perform(patch("/api/inbox/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"archived\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(post("/api/inbox")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"text\":\"Без CSRF\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/inbox"))
                .andExpect(status().isUnauthorized());

        setTasksEnabled(owner, false);
        mvc.perform(get("/api/inbox").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.moduleKey").value("tasks"));
    }

    private void setTasksEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"tasks\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
