package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDetailsFrontendContractTest {
    private static final Path STATIC = Path.of("src/main/resources/static");

    @Test
    void readFirstDetailsModalIsSeparateFromTheEditor() throws Exception {
        String html = Files.readString(STATIC.resolve("index.html"));
        String js = Files.readString(STATIC.resolve("js/50-tasks.js"));
        String data = Files.readString(STATIC.resolve("js/20-data.js"));

        assertTrue(html.contains("id=\"taskDetailsModal\""));
        assertTrue(html.contains("id=\"taskDetailsDescriptionText\""));
        assertTrue(html.contains("id=\"taskDetailsEdit\""));
        assertTrue(html.contains("id=\"taskEditDescription\""));
        assertTrue(data.contains("async task(id)"));
        assertTrue(js.contains("async function openTaskDetails"));
        assertTrue(js.contains("body.addEventListener(\"click\", () => openTaskDetails(task))"));
        assertFalse(js.contains("body.addEventListener(\"click\", () => editTask(task))"));
    }

    @Test
    void detailsKeepChecklistActionsAndDescriptionInTheAuthoritativeDto() throws Exception {
        String js = Files.readString(STATIC.resolve("js/50-tasks.js"));
        String css = Files.readString(STATIC.resolve("app.css"));
        String dto = Files.readString(Path.of("src/main/java/ru/daniil/shifts/dto/Dtos.java"));
        String controller = Files.readString(Path.of("src/main/java/ru/daniil/shifts/web/TaskController.java"));

        assertTrue(js.contains("description:$(\"taskEditDescription\").value.trim()"));
        assertTrue(js.contains("toggleSubtask(task.id, subtask.id"));
        assertTrue(js.contains("taskDetailsToggle"));
        assertTrue(css.contains(".taskDetailsPanel"));
        assertTrue(dto.contains("String description"));
        assertTrue(controller.contains("@GetMapping(\"/{id}\")"));
    }
}
