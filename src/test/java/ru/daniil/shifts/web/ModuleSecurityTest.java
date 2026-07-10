package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.time.Instant;
import ru.daniil.shifts.repo.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security review regressions for the modular release line.
 *
 * These tests are intentionally high-level: module switches must protect web API and
 * the aggregated mobile sync endpoint, not only hide UI blocks in the browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModuleSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository mobileTokens;

    AppUser regular;
    String accessToken;

    @BeforeEach
    void setUp() {
        regular = users.save(new AppUser("module-sec-user", "{noop}x"));
        accessToken = "module-sec-access";
        mobileTokens.saveAndFlush(new MobileAuthToken(
                regular,
                MobileAuthService.hash(accessToken),
                MobileAuthService.hash("module-sec-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "test-device"));
    }

    @Test
    void mobileSyncCannotWriteNotesWhenNotesModuleDisabled() throws Exception {
        disableModule("notes");

        mvc.perform(post("/api/mobile/sync")
                        .header(AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"days\":[{\"date\":\"2026-07-10\",\"note\":\"hidden note\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mobileSyncCannotWriteOvertimeWhenOvertimeModuleDisabled() throws Exception {
        disableModule("overtime");

        mvc.perform(post("/api/mobile/sync")
                        .header(AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"days\":[{\"date\":\"2026-07-10\",\"overtimeHours\":2.5}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void browserSecurityHeadersArePresent() throws Exception {
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        not(containsString("script-src 'self' 'unsafe-inline'"))));
    }

    private void disableModule(String key) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(regular.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"" + key + "\":false}}"))
                .andExpect(status().isOk());
    }
}
