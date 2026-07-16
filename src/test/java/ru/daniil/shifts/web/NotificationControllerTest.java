package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API boundary tests for the optional notifications module. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("notification-controller-user", "{noop}unused"));
    }

    @Test
    void disabledModuleGuardsSettingsAndUpcomingEndpoints() throws Exception {
        setNotificationsEnabled(false);

        mvc.perform(get("/api/notifications/settings")
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:notifications"));

        mvc.perform(get("/api/notifications/upcoming")
                        .with(user(user.getUsername()).roles("USER"))
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("MODULE_DISABLED:notifications"));
    }

    @Test
    void enabledModuleReturnsSettingsAndValidatesReminderBounds() throws Exception {
        setNotificationsEnabled(true);

        mvc.perform(get("/api/notifications/settings")
                        .with(user(user.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftRemindersEnabled").value(true))
                .andExpect(jsonPath("$.taskRemindersEnabled").value(true));

        mvc.perform(patch("/api/notifications/settings")
                        .with(user(user.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"shiftReminderMinutesBefore\":1441}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void upcomingRejectsMalformedDateRange() throws Exception {
        setNotificationsEnabled(true);

        mvc.perform(get("/api/notifications/upcoming")
                        .with(user(user.getUsername()).roles("USER"))
                        .param("from", "16.07.2026")
                        .param("to", "2026-07-31"))
                .andExpect(status().isBadRequest());
    }

    private void setNotificationsEnabled(boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(user.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"notifications\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
