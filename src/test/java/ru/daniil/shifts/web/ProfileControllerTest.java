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
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.service.ShiftTypeService;
import ru.daniil.shifts.service.TaskService;
import ru.daniil.shifts.service.QuickScenarioService;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.QuickScenarioCreateRequest;
import ru.daniil.shifts.model.TaskPriority;

import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP and persistence contract for profile, locale and safe theme settings. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shiftTypes;
    @Autowired ShiftTypeService shiftTypeService;
    @Autowired TaskService taskService;
    @Autowired QuickScenarioService quickScenarioService;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("profile-controller-owner", "{noop}unused"));
    }

    @Test
    void getReturnsSafeDefaultsAndNeverExposesPasswordHash() throws Exception {
        mvc.perform(get("/api/profile").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.displayName").value(nullValue()))
                .andExpect(jsonPath("$.birthday").value(nullValue()))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accountTier").value("FREE"))
                .andExpect(jsonPath("$.themePreference").value("system"))
                .andExpect(jsonPath("$.accentColor").value("#F5B841"))
                .andExpect(jsonPath("$.themePreset").value("default"))
                .andExpect(jsonPath("$.themeConfig").isMap())
                .andExpect(jsonPath("$.languagePreference").value("ru"))
                .andExpect(jsonPath("$.workTimezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.displayTimezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void fullUpdateTrimsNormalizesClampsAndPersistsAllowedThemeFields() throws Exception {
        String body = """
                {
                  "displayName":"  Алексей QA  ",
                  "birthday":"2000-02-29",
                  "themePreference":" DARK ",
                  "accentColor":"#abcdef",
                  "themePreset":"custom_1",
                  "themeConfig":{
                    "appBg":"#101010",
                    "panelBg":"#202020",
                    "textColor":"#fefefe",
                    "buttonStyle":"soft",
                    "cardStyle":"contrast",
                    "shadowLevel":"strong",
                    "density":"compact",
                    "shellMode":"classic",
                    "cardRadius":999,
                    "uiContract":1,
                    "workspaceId":"planner",
                    "layoutId":"focus",
                    "themeId":"midnight",
                    "paletteId":"violet",
                    "decorationId":"none",
                    "accentSecondary":"#123456",
                    "todayWidgets":["tasks","important","tasks","javascript"],
                    "unknownCss":"body{display:none}"
                  },
                  "languagePreference":" EN ",
                  "workTimezone":"Europe/Chisinau",
                  "displayTimezone":"Europe/Berlin",
                  "onboardingCompleted":true
                }
                """;

        mvc.perform(put("/api/v1/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Алексей QA"))
                .andExpect(jsonPath("$.birthday").value("2000-02-29"))
                .andExpect(jsonPath("$.themePreference").value("dark"))
                .andExpect(jsonPath("$.accentColor").value("#ABCDEF"))
                .andExpect(jsonPath("$.themePreset").value("custom_1"))
                .andExpect(jsonPath("$.languagePreference").value("en"))
                .andExpect(jsonPath("$.workTimezone").value("Europe/Chisinau"))
                .andExpect(jsonPath("$.displayTimezone").value("Europe/Chisinau"))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.themeConfig.appBg").value("#101010"))
                .andExpect(jsonPath("$.themeConfig.textColor").value("#FEFEFE"))
                .andExpect(jsonPath("$.themeConfig.buttonStyle").value("soft"))
                .andExpect(jsonPath("$.themeConfig.shellMode").value("classic"))
                .andExpect(jsonPath("$.themeConfig.cardRadius").value(28))
                .andExpect(jsonPath("$.themeConfig.uiContract").value(1))
                .andExpect(jsonPath("$.themeConfig.workspaceId").value("planner"))
                .andExpect(jsonPath("$.themeConfig.layoutId").value("focus"))
                .andExpect(jsonPath("$.themeConfig.themeId").value("midnight"))
                .andExpect(jsonPath("$.themeConfig.paletteId").value("violet"))
                .andExpect(jsonPath("$.themeConfig.decorationId").value("none"))
                .andExpect(jsonPath("$.themeConfig.accentSecondary").value("#123456"))
                .andExpect(jsonPath("$.themeConfig.todayWidgets[0]").value("tasks"))
                .andExpect(jsonPath("$.themeConfig.todayWidgets[1]").value("important"))
                .andExpect(jsonPath("$.themeConfig.todayWidgets.length()").value(2))
                .andExpect(jsonPath("$.themeConfig.unknownCss").doesNotExist());

        AppUser stored = users.findByUsername(owner.getUsername()).orElseThrow();
        assertEquals("Алексей QA", stored.getDisplayName());
        assertEquals(LocalDate.of(2000, 2, 29), stored.getBirthday());
        assertEquals("dark", stored.getThemePreference());
        assertEquals("#ABCDEF", stored.getAccentColor());
        assertEquals("custom_1", stored.getThemePreset());
        assertEquals("en", stored.getLanguagePreference());
        assertEquals("Europe/Chisinau", stored.getWorkTimezone());
        assertEquals("Europe/Chisinau", stored.getDisplayTimezone());
        assertTrue(stored.isOnboardingCompleted());
        assertTrue(stored.getThemeConfig().contains("\"cardRadius\":28"));
        assertTrue(stored.getThemeConfig().contains("\"shellMode\":\"classic\""));
        assertTrue(stored.getThemeConfig().contains("\"workspaceId\":\"planner\""));
        assertTrue(stored.getThemeConfig().contains("\"todayWidgets\":[\"tasks\",\"important\"]"));
        assertTrue(!stored.getThemeConfig().contains("unknownCss"));
    }

    @Test
    void eitherLegacyTimezoneFieldUpdatesTheSingleCanonicalTimezone() throws Exception {
        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"workTimezone\":\"Europe/Chisinau\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workTimezone").value("Europe/Chisinau"))
                .andExpect(jsonPath("$.displayTimezone").value("Europe/Chisinau"));

        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"displayTimezone\":\"Europe/Berlin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workTimezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.displayTimezone").value("Europe/Berlin"));
    }

    @Test
    void canonicalTimezoneUpdateAlsoRebasesFutureShiftTemplates() throws Exception {
        owner.setWorkTimezone("Asia/Yekaterinburg");
        owner.setDisplayTimezone("Asia/Yekaterinburg");
        users.save(owner);
        shiftTypeService.list(owner);
        quickScenarioService.create(owner, new QuickScenarioCreateRequest(
                "До утра", null, null,
                "SHIFT_END", "FIXED_TIME", 0, "08:00", true, 1,
                "ZERO", 0, "ZERO", 0.0, null, 10));

        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"workTimezone\":\"Europe/Moscow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workTimezone").value("Europe/Moscow"));

        var day = shiftTypes.findByOwnerAndName(owner, "Дневная").get(0);
        assertEquals("06:30", day.getStartTime().toString());
        assertEquals("15:00", day.getEndTime().toString());
        var projectedScenario = quickScenarioService.list(owner).get(0);
        assertEquals("06:00", projectedScenario.endFixedTime());
        assertEquals(1, projectedScenario.endDayOffset());
    }

    @Test
    void canonicalTimezoneUpdateAlsoReprojectsTimedTaskDeadlines() throws Exception {
        owner.setWorkTimezone("Asia/Yekaterinburg");
        owner.setDisplayTimezone("Asia/Yekaterinburg");
        owner = users.save(owner);
        var task = taskService.create(owner, new TaskCreateRequest(
                "2035-07-26", "Созвон", null, null, TaskPriority.NORMAL,
                "2035-07-26", "14:10", true, 0));

        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"workTimezone\":\"Europe/Moscow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workTimezone").value("Europe/Moscow"));

        AppUser storedUser = users.findByUsername(owner.getUsername()).orElseThrow();
        var projected = taskService.get(storedUser, task.id());
        assertEquals("2035-07-26", projected.dueDate());
        assertEquals("12:10", projected.dueTime());
        assertTrue(projected.deadlineAbsolute());
        assertEquals("Asia/Yekaterinburg", projected.dueSourceTimezone());
        assertEquals("14:10", projected.dueSourceTime());
    }

    @Test
    void blankNameAndBirthdayExplicitlyClearExistingValuesWithoutResettingOtherPreferences() throws Exception {
        owner.setDisplayName("Старое имя");
        owner.setBirthday(LocalDate.of(1999, 1, 2));
        owner.setThemePreference("dark");
        owner.setLanguagePreference("en");
        users.save(owner);

        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"displayName\":\"   \",\"birthday\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(nullValue()))
                .andExpect(jsonPath("$.birthday").value(nullValue()))
                .andExpect(jsonPath("$.themePreference").value("dark"))
                .andExpect(jsonPath("$.languagePreference").value("en"));

        AppUser stored = users.findByUsername(owner.getUsername()).orElseThrow();
        assertNull(stored.getDisplayName());
        assertNull(stored.getBirthday());
    }

    @Test
    void corruptStoredThemeJsonIsReadAsAnEmptySafeObject() throws Exception {
        owner.setThemeConfig("definitely-not-json");
        users.save(owner);

        mvc.perform(get("/api/profile").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeConfig").isEmpty());
    }

    @Test
    void invalidProfileAndThemeValuesAlwaysUseBadRequestEnvelope() throws Exception {
        String future = LocalDate.now().plusDays(3).toString();
        String longName = "x".repeat(61);
        String[] bodies = {
                "{\"displayName\":\"" + longName + "\"}",
                "{\"birthday\":\"10.08.2000\"}",
                "{\"birthday\":\"" + future + "\"}",
                "{\"themePreference\":\"sepia\"}",
                "{\"accentColor\":\"#12\"}",
                "{\"themePreset\":\"bad preset!\"}",
                "{\"languagePreference\":\"ro\"}",
                "{\"themeConfig\":{\"appBg\":\"red\"}}",
                "{\"themeConfig\":{\"buttonStyle\":\"javascript\"}}",
                "{\"themeConfig\":{\"shellMode\":\"immersive\"}}",
                "{\"themeConfig\":{\"workspaceId\":\"chaos\"}}",
                "{\"themeConfig\":{\"layoutId\":\"absolute\"}}",
                "{\"themeConfig\":{\"themeId\":\"javascript\"}}",
                "{\"themeConfig\":{\"paletteId\":\"rainbow-script\"}}",
                "{\"themeConfig\":{\"accentSecondary\":\"orange\"}}",
                "{\"workTimezone\":\"Mars/Olympus_Mons\"}",
                "{\"workTimezone\":\"\"}",
                "{\"displayTimezone\":\"Mars/Olympus_Mons\"}",
                "{\"displayTimezone\":\"\"}"
        };

        for (String body : bodies) {
            mvc.perform(put("/api/profile")
                            .with(user(owner.getUsername()).roles("USER")).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }

    @Test
    void profileRequiresAuthenticationAndCsrfForChanges() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/profile")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"languagePreference\":\"en\"}"))
                .andExpect(status().isForbidden());
    }
}
