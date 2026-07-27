package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract protecting the independent-note UI and its offline boundary. */
class MultipleDailyNotesFrontendContractTest {

    @Test
    void htmlExposesListEditorAndIndependentActions() throws Exception {
        String html = read("src/main/resources/static/index.html");
        assertTrue(html.contains("id=\"noteAdd\""));
        assertTrue(html.contains("id=\"noteList\""));
        assertTrue(html.contains("id=\"noteTitle\""));
        assertTrue(html.contains("id=\"notePin\""));
        assertTrue(html.contains("id=\"noteMoveUp\""));
        assertTrue(html.contains("id=\"noteMoveDown\""));
        assertTrue(html.contains("id=\"noteDelete\""));
    }

    @Test
    void frontendUsesDedicatedCrudAndMergesDebouncedTitleAndContentPatches() throws Exception {
        String data = read("src/main/resources/static/js/20-data.js");
        String notes = read("src/main/resources/static/js/50-tasks.js");
        assertTrue(data.contains("async createDayNote"));
        assertTrue(data.contains("async updateDayNote"));
        assertTrue(data.contains("async moveDayNote"));
        assertTrue(data.contains("async deleteDayNote"));
        assertTrue(notes.contains("function renderDayNotes()"));
        assertTrue(notes.contains("patch:{ ...(sameNote ? pendingNoteSave.patch : {}), ...patch }"));
        assertTrue(notes.contains("Оффлайн доступно чтение snapshot"));
    }

    @Test
    void migrationApiOpenApiAndE2eCoverTheNewBoundary() throws Exception {
        String migration = read("src/main/resources/db/migration/postgresql/V36__multiple_daily_notes.sql");
        String openapi = read("src/main/resources/static/openapi/dutylog-v1.yaml");
        String e2e = read("e2e/multiple-daily-notes.spec.js");
        assertTrue(migration.contains("CREATE TABLE day_notes"));
        assertTrue(migration.contains("INSERT INTO day_notes"));
        assertTrue(openapi.contains("/api/v1/notes:"));
        assertTrue(openapi.contains("DayNote:"));
        assertTrue(e2e.contains("multiple notes on one day remain independent"));
        assertTrue(e2e.contains("noteCountBadge"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
