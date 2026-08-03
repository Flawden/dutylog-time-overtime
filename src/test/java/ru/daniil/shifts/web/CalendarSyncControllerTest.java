package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantDay;
import ru.daniil.shifts.model.RepeatMode;
import ru.daniil.shifts.repo.ImportantDayRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalendarSyncControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ImportantDayRepository importantDays;
    @Autowired ObjectMapper objectMapper;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("calendar-sync-owner", "{noop}unused"));
    }

    @Test
    void inactiveStatusIsOwnerScopedAndNeverReturnsASecret() throws Exception {
        mvc.perform(get("/api/v1/calendar-sync/status")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.tokenHint").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.subscriptionUrl").doesNotExist())
                .andExpect(jsonPath("$.feedPastDays").value(30))
                .andExpect(jsonPath("$.feedFutureDays").value(335))
                .andExpect(jsonPath("$.entities.length()").value(4));
    }

    @Test
    void issueRotateFeedAndRevokeFormOnePrivateTokenLifecycle() throws Exception {
        JsonNode first = issueSubscription();
        org.junit.jupiter.api.Assertions.assertTrue(first.path("tokenHint").asText().contains("\u2026"));
        String firstUrl = first.path("subscriptionUrl").asText();
        String firstToken = tokenFrom(firstUrl);

        mvc.perform(get("/calendar-feed.ics").param("token", firstToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/calendar")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR\r\n")))
                .andExpect(content().string(containsString("PRODID:-//DutyLog//Time and Overtime 27.31.0//RU")));

        mvc.perform(get("/api/calendar-sync/status")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.tokenHint").value(first.path("tokenHint").asText()))
                .andExpect(content().string(not(containsString(firstToken))))
                .andExpect(content().string(not(containsString("subscriptionUrl"))));

        JsonNode rotated = issueSubscription();
        String secondToken = tokenFrom(rotated.path("subscriptionUrl").asText());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstToken, secondToken);

        mvc.perform(get("/calendar-feed.ics").param("token", firstToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/calendar-feed.ics").param("token", secondToken))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/calendar-sync/subscription")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        mvc.perform(get("/calendar-feed.ics").param("token", secondToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rangeAndSingleEventExportsAreUtf8CalendarAttachments() throws Exception {
        ImportantDay event = importantDays.save(new ImportantDay(
                owner, "Отпуск, встреча", LocalDate.of(2026, 8, 15), RepeatMode.YEARLY, "#F5B841"));

        mvc.perform(get("/api/v1/calendar-sync/export")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01").param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/calendar")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("SUMMARY:Отпуск\\, встреча")))
                .andExpect(content().string(containsString("END:VCALENDAR\r\n")));

        mvc.perform(get("/api/calendar-sync/events/{id}.ics", event.getId())
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("dutylog-event-")))
                .andExpect(content().string(containsString("UID:important-" + event.getId() + "@dutylog")))
                .andExpect(content().string(containsString("RRULE:FREQ=YEARLY")));
    }

    @Test
    void authenticationCsrfAndMalformedTokensStayOnStableBoundaries() throws Exception {
        mvc.perform(get("/api/calendar-sync/status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/calendar-sync/subscription")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/calendar-sync/subscription")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/calendar-feed.ics").param("token", "not-a-secret"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/calendar-sync/export")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-31").param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/calendar-sync/export")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-01-01").param("to", "2027-01-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disablingCalendarSyncBlocksAuthenticatedExportsAndHidesAnExistingFeed() throws Exception {
        String token = tokenFrom(issueSubscription().path("subscriptionUrl").asText());

        mvc.perform(patch("/api/modules")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"calendar_sync\":false}}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/calendar-sync/status")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/calendar-sync/export")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-08-01").param("to", "2026-08-02"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/calendar-feed.ics").param("token", token))
                .andExpect(status().isNotFound());
    }

    private JsonNode issueSubscription() throws Exception {
        String json = mvc.perform(post("/api/v1/calendar-sync/subscription")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.subscriptionUrl", containsString("/calendar-feed.ics?token=")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private static String tokenFrom(String url) {
        String query = URI.create(url).getRawQuery();
        return query.substring("token=".length());
    }
}
