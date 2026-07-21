package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAndShiftEditorsFrontendContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void taskEditingUsesOneStructuredModalInsteadOfPromptChain() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");

        assertTrue(html.contains("id=\"taskEditModal\""));
        assertTrue(html.contains("id=\"taskEditText\""));
        assertTrue(html.contains("id=\"taskEditPriority\""));
        assertTrue(html.contains("id=\"taskEditReminderEnabled\""));
        assertTrue(tasks.contains("openAppModal(\"taskEditModal\""));
        assertTrue(tasks.contains("async function saveTaskEditor()"));
        assertFalse(tasks.contains("prompt(\"Текст задачи\""));
        assertFalse(tasks.contains("confirm(t(\"Включить напоминание для этой задачи?\"))"));
    }

    @Test
    void shiftTypeManagerLivesBehindCalendarPlusInsteadOfSettingsCard() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String tasks = read("src/main/resources/static/js/50-tasks.js");
        String settings = read("src/main/resources/static/js/60-settings.js");

        assertTrue(html.contains("id=\"shiftTypeModal\""));
        assertTrue(html.contains("id=\"shiftTypeForm\""));
        assertTrue(html.contains("id=\"customList\""));
        assertFalse(html.contains("id=\"shiftSettingsCard\""));
        assertFalse(html.contains("data-settings-jump=\"shifts\""));
        assertTrue(tasks.contains("openShiftTypeManager()"));
        assertTrue(settings.contains("async function saveShiftTypeForm()"));
        assertFalse(settings.contains("prompt(t(\"Название смены\")"));
        assertFalse(settings.contains("prompt(t(\"Короткие часы для календаря\")"));
    }
}
