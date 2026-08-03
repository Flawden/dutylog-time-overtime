package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.30.2 Today overtime journal routing. */
class TodayOvertimeJournalContractHotfixTest {

    @Test
    void todayOvertimeCardOpensTheJournalWhileCreditCreationRemainsInOvertime() throws Exception {
        String html = read("src/main/resources/static/index.html");
        String today = read("src/main/resources/static/js/35-today.js");
        String overtime = read("src/main/resources/static/js/40-overtime.js");

        assertTrue(html.contains("id=\"todayOpenOvertime\"")
                && html.contains(">Журнал</button>"));
        assertTrue(today.contains("$(\"todayOpenOvertime\")?.addEventListener(\"click\", () => { location.hash = \"#overtime\"; });"));
        assertFalse(today.contains("openOvertimeCreditModal"));

        assertTrue(overtime.contains("function openOvertimeCreditModal(date = null)"));
        assertTrue(overtime.contains("$(\"dayAddCredit\")?.addEventListener(\"click\", () => openOvertimeCreditModal(state.selected))"));
        assertTrue(overtime.contains("$(\"ledgerAddCredit\")?.addEventListener(\"click\", () => openOvertimeCreditModal(state.selected || todayKey()))"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
