package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for v27.19.0 Tasks & Inbox Next. */
class TasksInboxNextFrontendContractTest {

    @Test
    void taskEditorSeparatesPlanningFromDeadlineAndSupportsProjects() throws Exception {
        String html = read("src/main/resources/static/index.html");
        assertTrue(html.contains("id=\"taskEditAllDay\""));
        assertTrue(html.contains("id=\"taskEditStartTime\""));
        assertTrue(html.contains("id=\"taskEditEndDate\""));
        assertTrue(html.contains("id=\"taskEditEndTime\""));
        assertTrue(html.contains("id=\"taskEditDuration\""));
        assertTrue(html.contains("data-task-duration=\"15\""));
        assertTrue(html.contains("id=\"taskEditProject\""));
        assertTrue(html.contains("id=\"taskEditDueDate\""));
    }

    @Test
    void readFirstDetailsAndBoardExposeCanonicalPlanning() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String js = read("src/main/resources/static/js/50-tasks.js");
        assertTrue(html.contains("id=\"taskDetailsScheduleMain\""));
        assertTrue(html.contains("id=\"taskDetailsScheduleSource\""));
        assertTrue(html.contains("id=\"taskBoardProject\""));
        assertTrue(js.contains("function validateTaskPlanning()"));
        assertTrue(js.contains("function renderTaskBoardProjectFilter()"));
        assertTrue(js.contains("addTaskToDateMap(state.tasksByDate, task)"));
    }

    @Test
    void hourlyCalendarUsesPlannedIntervalsInsteadOfDeadlines() throws Exception {
        String js = read("src/main/resources/static/js/37-calendar-experience.js");
        assertTrue(js.contains("function calendarExperienceTaskSegment(task, key)"));
        assertTrue(js.contains("task.scheduledStartTime"));
        assertTrue(js.contains("task.scheduledEndTime"));
        assertTrue(js.contains("event.point ? start"));
    }

    @Test
    void inboxSearchAndResponsiveEditorRemainAvailable() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String css = read("src/main/resources/static/app.css");
        String js = read("src/main/resources/static/js/50-tasks.js");
        assertTrue(html.contains("id=\"inboxSearch\""));
        assertTrue(js.contains("state.inbox.q"));
        assertTrue(css.contains("/* v27.19.0 — Tasks & Inbox Next */"));
        assertTrue(css.contains(".taskEditorPanel"));
        assertTrue(css.contains(".taskPlanningGrid"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
