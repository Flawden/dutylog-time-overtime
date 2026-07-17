package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLink;
import ru.daniil.shifts.repo.TelegramLinkCodeRepository;
import ru.daniil.shifts.repo.TelegramLinkRepository;
import ru.daniil.shifts.repo.UserRepository;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browser API boundary for Telegram linking and notification preferences. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelegramControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TelegramLinkRepository links;
    @Autowired TelegramLinkCodeRepository codes;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("telegram-controller-owner", "{noop}x"));
    }

    @Test
    void disabledModuleGuardsEveryTelegramEndpoint() throws Exception {
        setTelegramEnabled(false);

        mvc.perform(get("/api/telegram/status").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.moduleKey").value("telegram"));
        mvc.perform(post("/api/telegram/link-code")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.moduleKey").value("telegram"));
        mvc.perform(patch("/api/telegram/settings")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content("{\"notificationsEnabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/telegram/link")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusAndLinkCodeHaveStableSafeShape() throws Exception {
        setTelegramEnabled(true);

        mvc.perform(get("/api/telegram/status")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.notificationsEnabled").value(false))
                .andExpect(jsonPath("$.chatId").doesNotExist())
                .andExpect(jsonPath("$.pendingCode").doesNotExist());

        mvc.perform(post("/api/telegram/link-code")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", matchesPattern("DL-[0-9]{6}")))
                .andExpect(jsonPath("$.startCommand", matchesPattern("/start DL-[0-9]{6}")))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.deepLink").doesNotExist());

        mvc.perform(get("/api/telegram/status")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCode", matchesPattern("DL-[0-9]{6}")))
                .andExpect(jsonPath("$.pendingCodeExpiresAt").isNotEmpty());
    }

    @Test
    void settingsRequireLinkAndThenPersistUntilUnlink() throws Exception {
        setTelegramEnabled(true);

        mvc.perform(patch("/api/telegram/settings")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"notificationsEnabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        TelegramLink link = links.save(new TelegramLink(owner, 777L));
        link.setUsername("telegram_name");
        links.save(link);

        mvc.perform(patch("/api/telegram/settings")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"notificationsEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.chatId").value("777"))
                .andExpect(jsonPath("$.username").value("telegram_name"))
                .andExpect(jsonPath("$.notificationsEnabled").value(false));

        mvc.perform(delete("/api/telegram/link")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(links.findByOwner(owner).isEmpty());
        assertTrue(codes.findByOwnerAndUsedAtIsNullOrderByCreatedAtDesc(owner).isEmpty());
    }

    @Test
    void csrfAndAuthenticationAreRequiredForMutationsAndReads() throws Exception {
        setTelegramEnabled(true);

        mvc.perform(post("/api/telegram/link-code")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/telegram/settings")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"notificationsEnabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/telegram/link")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/telegram/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unlinkAlsoInvalidatesUnusedLinkCodes() throws Exception {
        setTelegramEnabled(true);
        mvc.perform(post("/api/telegram/link-code")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isOk());
        assertFalse(codes.findByOwnerAndUsedAtIsNullOrderByCreatedAtDesc(owner).isEmpty());

        mvc.perform(delete("/api/telegram/link")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(codes.findByOwnerAndUsedAtIsNullOrderByCreatedAtDesc(owner).isEmpty());
    }

    private void setTelegramEnabled(boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"telegram\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
