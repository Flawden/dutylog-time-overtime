package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobileTasksInboxFrontendContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void dayAndBoardUseDedicatedTaskEditorInsteadOfInlineCreationForm() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");

        assertTrue(html.contains("id=\"taskCreateForDay\""));
        assertTrue(html.contains("id=\"taskBoardCreate\""));
        assertTrue(html.contains("id=\"taskEditAdvanced\""));
        assertTrue(html.contains("id=\"taskEditTags\""));
        assertTrue(html.contains("id=\"taskEditDueTime\""));
        assertTrue(html.contains("type=\"time\""));
        assertFalse(html.contains("id=\"taskText\""));
        assertFalse(html.contains("id=\"taskAdd\""));
        assertTrue(tasks.contains("function openTaskCreate(options"));
        assertTrue(tasks.contains("function parseTaskTags(value)"));
        assertTrue(tasks.contains("\"Быстрое действие\":\"Quick action\""));
        assertTrue(tasks.contains("\"Списать переработку\":\"Use overtime\""));
    }

    @Test
    void fastCaptureInboxAndOfflineQueueAreConnectedEndToEnd() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String core = read("src/main/resources/static/js/10-core.js");
        String data = read("src/main/resources/static/js/20-data.js");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String modules = read("src/main/java/ru/daniil/shifts/module/DutyLogModules.java");

        assertTrue(html.contains("id=\"taskInboxCard\""));
        assertTrue(html.contains("id=\"quickCaptureModal\""));
        assertTrue(html.contains("id=\"globalQuickAdd\""));
        assertTrue(html.contains("Списать переработку"));
        assertTrue(core.contains("quickCaptureModal"));
        assertTrue(data.contains("captureInbox"));
        assertTrue(data.contains("/api/inbox"));
        assertTrue(tasks.contains("async function captureInbox(text)"));
        assertTrue(tasks.contains("inboxId:item.id"));
        assertTrue(modules.contains("inbox.capture"));
        assertTrue(modules.contains("/api/inbox"));
    }

    @Test
    void mobileCssMakesCaptureAndTaskEditingOneHandFriendly() throws Exception {
        String css = read("src/main/resources/static/app.css");

        assertTrue(css.contains(".globalQuickAdd"));
        assertTrue(css.contains("#taskEditModal .appModalPanel"));
        assertTrue(css.contains("height:100dvh"));
        assertTrue(css.contains(".quickActionGrid"));
        assertTrue(css.contains(".inboxCaptureRow"));
    }
}
