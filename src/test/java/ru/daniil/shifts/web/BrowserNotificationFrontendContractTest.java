package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser-runtime regression guards for the bugs found during the v27.2.6 manual pass.
 * These are source contracts because the current frontend is vanilla ordered scripts;
 * backend reminder calculations are covered behaviourally in NotificationServiceTest.
 */
class BrowserNotificationFrontendContractTest {

    @Test
    void moduleToggleStopsPollingAndGuarded403CannotBecomeARecurringLoop() throws IOException {
        String data = resource("/static/js/20-data.js");
        String settings = resource("/static/js/60-settings.js");

        assertTrue(data.contains("syncBrowserNotificationSchedulerForModules();"),
                "setModuleList must synchronize the running scheduler after every module response");
        assertTrue(data.contains("moduleKey = body?.moduleKey || null"),
                "jfetch must prefer the structured moduleKey field from the server");
        assertTrue(data.contains("err.moduleKey = moduleKey"),
                "jfetch must preserve the disabled module key instead of only translating its message");

        assertTrue(settings.contains("function stopBrowserNotificationScheduler()")
                        && settings.contains("clearInterval(browserNotificationTimer)")
                        && settings.contains("browserNotificationTimer = null"),
                "disabling notifications must tear down the active interval");
        assertTrue(settings.contains("if (!state.modulesLoaded || !moduleEnabled(\"notifications\"))")
                        && settings.contains("stopBrowserNotificationScheduler();"),
                "scheduler startup must be guarded by the current module map");
        assertTrue(settings.contains("err?.moduleKey === \"notifications\"")
                        && settings.contains("try { await loadModules(); }"),
                "a stale-map MODULE_DISABLED response must stop polling and resynchronize modules");
        assertTrue(settings.contains("if (!state.modulesLoaded || !moduleEnabled(\"notifications\")) return;")
                        && settings.contains("const fetched = await api.notificationUpcoming"),
                "an in-flight response must not be delivered after the module is switched off");
    }

    @Test
    void taskReminderControlsStayDisabledWhenNotificationsModuleIsOff() throws IOException {
        String tasks = resource("/static/js/50-tasks.js");
        assertTrue(tasks.contains("function updateTaskReminderControls()"));
        assertTrue(tasks.contains("moduleEnabled(\"notifications\")"));
        assertTrue(tasks.contains("taskReminderModuleHint"));
    }

    private static String resource(String path) throws IOException {
        try (var in = BrowserNotificationFrontendContractTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Missing classpath resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
