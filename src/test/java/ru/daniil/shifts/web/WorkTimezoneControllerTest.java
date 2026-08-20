package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.UserTimeService;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkTimezoneControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    UserTimeService userTimeService;

    AppUser owner;

    @BeforeEach
    void setUp() {
        String suffix =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 12);

        owner =
                new AppUser(
                        "tz-api-" + suffix,
                        "{noop}unused"
                );

        owner.setWorkTimezone(
                "Asia/Yekaterinburg"
        );
        owner.setDisplayTimezone(
                "Asia/Yekaterinburg"
        );

        owner = users.saveAndFlush(owner);
    }

    @Test
    void historyExposesProtectedCompatibilityBaseline()
            throws Exception {

        assertBaseline(
                "/api/time/work-context"
        );

        assertBaseline(
                "/api/v1/time/work-context"
        );
    }

    @Test
    void currentDateChangeUpdatesCurrentCompatibilityCache()
            throws Exception {

        LocalDate today =
                userTimeService.workToday(owner);

        mvc.perform(
                        put("/api/v1/time/work-context")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "effectiveFrom":"%s",
                                          "timezone":"Europe/Moscow"
                                        }
                                        """.formatted(today))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.currentTimezone")
                                .value("Europe/Moscow")
                )
                .andExpect(
                        jsonPath("$.currentDate")
                                .isString()
                )
                .andExpect(
                        jsonPath("$.terms[1].effectiveFrom")
                                .value(today.toString())
                )
                .andExpect(
                        jsonPath("$.terms[1].timezone")
                                .value("Europe/Moscow")
                )
                .andExpect(
                        jsonPath("$.terms[1].baseline")
                                .value(false)
                );

        AppUser stored =
                users.findById(owner.getId())
                        .orElseThrow();

        assertEquals(
                "Europe/Moscow",
                stored.getWorkTimezone()
        );

        assertEquals(
                "Europe/Moscow",
                stored.getDisplayTimezone()
        );
    }

    @Test
    void middleHistoricalChangeDoesNotRewriteCurrentCache()
            throws Exception {

        LocalDate today =
                userTimeService.workToday(owner);

        putTimezone(
                today,
                "Europe/Moscow"
        );

        LocalDate historical =
                today.minusDays(10);

        mvc.perform(
                        put("/api/v1/time/work-context")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "effectiveFrom":"%s",
                                          "timezone":"Europe/Samara"
                                        }
                                        """.formatted(historical))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.currentTimezone")
                                .value("Europe/Moscow")
                )
                .andExpect(
                        jsonPath("$.terms[1].effectiveFrom")
                                .value(historical.toString())
                )
                .andExpect(
                        jsonPath("$.terms[1].timezone")
                                .value("Europe/Samara")
                )
                .andExpect(
                        jsonPath("$.terms[2].effectiveFrom")
                                .value(today.toString())
                )
                .andExpect(
                        jsonPath("$.terms[2].timezone")
                                .value("Europe/Moscow")
                );

        AppUser stored =
                users.findById(owner.getId())
                        .orElseThrow();

        assertEquals(
                "Europe/Moscow",
                stored.getWorkTimezone()
        );

        assertEquals(
                "Europe/Moscow",
                stored.getDisplayTimezone()
        );
    }

    @Test
    void futureAndProtectedBaselineAreRejected()
            throws Exception {

        LocalDate today =
                userTimeService.workToday(owner);

        mvc.perform(
                        put("/api/v1/time/work-context")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "effectiveFrom":"%s",
                                          "timezone":"Europe/Moscow"
                                        }
                                        """.formatted(today.plusDays(1)))
                )
                .andExpect(status().isBadRequest());

        mvc.perform(
                        put("/api/v1/time/work-context")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "effectiveFrom":"1970-01-01",
                                          "timezone":"Europe/Moscow"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    private void assertBaseline(
            String path
    ) throws Exception {

        mvc.perform(
                        get(path)
                                .with(user(owner.getUsername()).roles("USER"))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.currentTimezone")
                                .value("Asia/Yekaterinburg")
                )
                .andExpect(
                        jsonPath("$.currentDate")
                                .isString()
                )
                .andExpect(
                        jsonPath("$.terms[0].effectiveFrom")
                                .value("1970-01-01")
                )
                .andExpect(
                        jsonPath("$.terms[0].timezone")
                                .value("Asia/Yekaterinburg")
                )
                .andExpect(
                        jsonPath("$.terms[0].baseline")
                                .value(true)
                );
    }

    private void putTimezone(
            LocalDate effectiveFrom,
            String timezone
    ) throws Exception {

        mvc.perform(
                        put("/api/v1/time/work-context")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "effectiveFrom":"%s",
                                          "timezone":"%s"
                                        }
                                        """.formatted(
                                        effectiveFrom,
                                        timezone
                                ))
                )
                .andExpect(status().isOk());
    }
}
