package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimezoneProjectionRefreshFrontendContractTest {

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    void profileLoadsBeforeInitialCalendarProjection() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        int profile = boot.indexOf("await loadProfile();");
        int month = boot.indexOf("await loadMonth();");
        assertTrue(profile >= 0 && month > profile,
                "the first calendar request must use the persisted work/display zones");
        assertFalse(boot.contains("applyLanguage(state.language);\nloadProfile();"),
                "profile loading must not race init as a fire-and-forget call");
    }

    @Test
    void timezoneSaveForcesAuthoritativeCalendarReload() throws Exception {
        String settings = read("src/main/resources/static/js/60-settings.js");
        assertTrue(settings.contains("await loadMonth({ fresh:true })"));
        assertTrue(settings.contains("await loadLedgerPage(true)"));
    }

    @Test
    void freshCalendarLoadSkipsIndexedDbSnapshot() throws Exception {
        String data = read("src/main/resources/static/js/20-data.js");
        assertTrue(data.contains("const snap = fresh ? null : await this.readSnapshot()"));
        assertTrue(data.contains("api.month(y, m, { fresh })"));
    }

    @Test
    void loadMonthPropagatesFreshFlagToDataLayer() throws Exception {
        String boot = read("src/main/resources/static/js/70-user-boot.js");
        assertTrue(boot.contains("async function loadMonth(opts = {})"));
        assertTrue(boot.contains("{ fresh:!!opts.fresh }"));
    }
}
