package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable ownership/usability contract for v27.40.32. */
class FunctionalParitySweepIIMobileUsabilityContractTest {

    @Test
    void overtimeChartUsesCanonicalProjectionAndZeroMeansZeroPixels() throws IOException {
        String model = read("frontend/src/features/absence-time-bank/types/model.ts");
        String page = read("frontend/src/features/absence-time-bank/components/TimeBankPage.vue");
        String spec = read("frontend/src/features/absence-time-bank/types/model.spec.ts");

        assertTrue(model.contains("serverProjection?.dayEarnedHours"));
        assertTrue(model.contains("export function creditRowEarnedHours"));
        assertTrue(page.contains("if (!(hours > 0.0001)) return \"0%\""));
        assertTrue(page.contains("credit.projection?.sourceCreditHours ?? creditRowEarnedHours(credit)"));
        assertTrue(spec.contains("historical visible row reports zero hours"));
    }

    @Test
    void integrityProblemsAreGroupedForHumansAndCodesMoveBehindDetails() throws IOException {
        String page = read("frontend/src/features/absence-time-bank/components/TimeBankPage.vue");

        assertTrue(page.contains("integrityIssueGroups"));
        assertTrue(page.contains("Нужно проверить учёт"));
        assertTrue(page.contains("Технические детали"));
        assertTrue(page.contains("group.code"));
        assertFalse(page.contains("<b>{{ issue.code }}</b> — {{ issue.message }}"));
    }

    @Test
    void monthCalendarUsesShiftColorAsScannableDataOnMobile() throws IOException {
        String calendar = read("frontend/src/features/calendar-timeline/components/CalendarPage.vue");
        String css = read("frontend/src/features/calendar-timeline/calendar-timeline.css");
        String browser = read("e2e/calendar-mobile-experience.spec.js");

        assertTrue(calendar.contains("hasShift: Boolean(cellFacts(date).shift)"));
        assertTrue(calendar.contains("styles[\"--shift-color\"] = facts.shift.color"));
        assertTrue(calendar.contains(":aria-label=\"cellAriaLabel(date)\""));
        assertTrue(css.contains(".cell.hasShift:not(.hasAbsenceFact)"));
        assertTrue(css.contains(".cell.hasShift:not(.hasAbsenceFact) .shift { display:none; }"));
        assertTrue(browser.contains(".cell.hasShift"));
    }

    @Test
    void phoneBottomNavigationIsIconOnlyButKeepsAccessibleNames() throws IOException {
        String navigation = read("frontend/src/app/AppNavigation.vue");
        String css = read("frontend/src/styles/design-system.css");
        String browser = read("e2e/mobile-layout.spec.js");

        assertTrue(navigation.contains(":aria-label=\"item.labels[language]\""));
        assertTrue(navigation.contains("vue-shell-nav__label"));
        assertTrue(css.contains(".vue-shell-nav__item .vue-shell-nav__label { display: none; }"));
        assertTrue(browser.contains(".vue-shell-nav__label"));
        assertTrue(browser.contains("toHaveAttribute('aria-label'"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
