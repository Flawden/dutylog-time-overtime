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
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TaskPriority;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.TaskService;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for task CRUD, board filters, module guards, CSRF and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired TaskService taskService;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("task-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("task-controller-other", "{noop}unused"));
    }

    @Test
    void fullCrudWorksAcrossLegacyAndV1Aliases() throws Exception {
        setTasksEnabled(owner, true);

        String createdBody = mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "date":"2026-08-10",
                                  "text":"Подготовить отчёт",
                                  "category":"Работа",
                                  "priority":"HIGH",
                                  "dueDate":"2026-08-11",
                                  "dueTime":"18:30",
                                  "reminderEnabled":true,
                                  "reminderMinutesBefore":30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.text").value("Подготовить отчёт"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(30))
                .andReturn().getResponse().getContentAsString();

        JsonNode createdJson = objectMapper.readTree(createdBody);
        long id = createdJson.path("id").asLong();

        mvc.perform(get("/api/v1/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].dueTime").value("18:30"));

        mvc.perform(patch("/api/tasks/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "text":"Отчёт готов",
                                  "done":true,
                                  "category":"Архив",
                                  "priority":"LOW",
                                  "reminderEnabled":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Отчёт готов"))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.reminderEnabled").value(false))
                .andExpect(jsonPath("$.reminderMinutesBefore").value(nullValue()));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));

        mvc.perform(delete("/api/v1/tasks/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void boardSupportsStatusCategoryPrioritySearchRangeAndPagination() throws Exception {
        setTasksEnabled(owner, true);
        LocalDate today = LocalDate.now();
        LocalDate past = today.minusDays(3);
        LocalDate future = today.plusDays(3);

        var overdue = taskService.create(owner, new TaskCreateRequest(
                past.minusDays(1).toString(), "Critical report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        var futureTask = taskService.create(owner, new TaskCreateRequest(
                today.toString(), "Buy milk", "Home", TaskPriority.LOW,
                future.toString(), "18:00", false, null));
        var done = taskService.create(owner, new TaskCreateRequest(
                past.minusDays(1).toString(), "Done report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));
        taskService.update(owner, done.id(), new TaskUpdateRequest(
                null, true, null, null, null, null, null, null, null));
        taskService.create(other, new TaskCreateRequest(
                past.minusDays(1).toString(), "Foreign report", "Work", TaskPriority.HIGH,
                past.toString(), null, false, null));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(overdue.id()));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "all")
                        .param("category", "work")
                        .param("priority", "high")
                        .param("q", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].id", hasItem(overdue.id().intValue())))
                .andExpect(jsonPath("$.items[*].id", hasItem(done.id().intValue())));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "all")
                        .param("from", future.toString())
                        .param("to", future.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(futureTask.id()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }


    @Test
    void deadlineRulesAreEnforcedAcrossLegacyAndV1Endpoints() throws Exception {
        setTasksEnabled(owner, true);

        mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-10\",\"text\":\"Неверный срок\",\"dueDate\":\"2026-08-09\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error").value("Срок не может быть раньше времени задачи."));

        String createdBody = mvc.perform(post("/api/v1/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "date":"2026-08-10",
                                  "text":"Подготовить пакет",
                                  "dueDate":"2026-08-11",
                                  "subtasks":[{"text":"Получить справку","dueDate":"2026-08-11"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtasks[0].dueDate").value("2026-08-11"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createdBody).path("id").asLong();
        mvc.perform(patch("/api/v1/tasks/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Срок не может быть раньше времени задачи."));

        mvc.perform(post("/api/v1/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "date":"2026-08-10",
                                  "text":"Неверная подзадача",
                                  "subtasks":[{"text":"Слишком рано","dueDate":"2026-08-09"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Срок подзадачи не может быть раньше даты задачи."));
    }

    @Test
    void validationAndMalformedParametersUseStableErrorEnvelope() throws Exception {
        setTasksEnabled(owner, true);

        mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-10\",\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.text").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"10.08.2026\",\"text\":\"Задача\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "mystery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields.page").value("invalid"));
    }

    @Test
    void subtasksAreReturnedInOrderAndHaveAnOwnerScopedToggleEndpoint() throws Exception {
        setTasksEnabled(owner, true);

        String body = mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "date":"2026-08-10",
                                  "text":"Подготовить релиз",
                                  "subtasks":[
                                    {"text":"Проверить CI","done":false,"sortOrder":8},
                                    {"text":"Проверить staging","done":false,"sortOrder":2}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtasks.length()").value(2))
                .andExpect(jsonPath("$.subtasks[0].text").value("Проверить CI"))
                .andExpect(jsonPath("$.subtasks[0].sortOrder").value(0))
                .andExpect(jsonPath("$.subtasks[1].sortOrder").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        long taskId = json.path("id").asLong();
        long subtaskId = json.path("subtasks").get(0).path("id").asLong();

        mvc.perform(patch("/api/tasks/{taskId}/subtasks/{subtaskId}", taskId, subtaskId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtasks[0].done").value(true))
                .andExpect(jsonPath("$.subtasks[1].done").value(false));

        setTasksEnabled(other, true);
        mvc.perform(patch("/api/tasks/{taskId}/subtasks/{subtaskId}", taskId, subtaskId)
                        .with(user(other.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(patch("/api/tasks/{id}", taskId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true,\"completeSubtasks\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.subtasks[0].done").value(true))
                .andExpect(jsonPath("$.subtasks[1].done").value(true));
    }

    @Test
    void disabledModuleGuardsAllTaskEndpointsWithoutDeletingExistingData() throws Exception {
        var existing = taskService.create(owner, new TaskCreateRequest(
                "2026-08-10", "Сохранённая задача", null, TaskPriority.NORMAL,
                null, null, false, null));
        setTasksEnabled(owner, false);

        mvc.perform(get("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-08-10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.moduleKey").value("tasks"))
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:tasks"));

        mvc.perform(get("/api/tasks/board")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:tasks"));

        mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-10\",\"text\":\"Новая\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:tasks"));

        mvc.perform(patch("/api/tasks/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/tasks/{taskId}/subtasks/{subtaskId}", existing.id(), 999L)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/tasks/{id}", existing.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        setTasksEnabled(owner, true);
        mvc.perform(get("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("date", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(existing.id()))
                .andExpect(jsonPath("$[0].text").value("Сохранённая задача"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        setTasksEnabled(owner, true);

        mvc.perform(post("/api/tasks")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"date\":\"2026-08-10\",\"text\":\"Без CSRF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/tasks").param("date", "2026-08-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void foreignTaskIdsRemainIndistinguishableFromMissingResources() throws Exception {
        setTasksEnabled(owner, true);
        setTasksEnabled(other, true);
        var foreign = taskService.create(other, new TaskCreateRequest(
                "2026-08-10", "Чужая задача", null, TaskPriority.NORMAL,
                null, null, false, null));

        mvc.perform(patch("/api/tasks/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"done\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/tasks/{id}", foreign.id())
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
