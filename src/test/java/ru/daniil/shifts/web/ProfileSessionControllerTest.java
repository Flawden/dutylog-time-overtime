package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browser-facing mobile-session management remains ownership-safe and CSRF-protected. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileSessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired MobileAuthService mobileAuthService;
    @Autowired PasswordEncoder encoder;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("profile-session-owner", encoder.encode("old-password")));
        other = users.save(new AppUser("profile-session-other", encoder.encode("other-password")));
    }

    @Test
    void sessionsListContainsOnlyCurrentUsersDevicesAndNeverReturnsRawTokens() throws Exception {
        mobileAuthService.issueTokenPairForRegisteredUser(owner, "Pixel 9");
        mobileAuthService.issueTokenPairForRegisteredUser(owner, "Tablet");
        mobileAuthService.issueTokenPairForRegisteredUser(other, "Foreign phone");

        mvc.perform(get("/api/profile/sessions").with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].deviceName", hasItems("Tablet", "Pixel 9")))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist());
    }

    @Test
    void ownerCanRevokeOneSessionAndRepeatedRevokeIsIdempotent() throws Exception {
        MobileTokenResponse issued = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Laptop PWA");
        MobileAuthToken token = tokens.findByAccessTokenHash(MobileAuthService.hash(issued.accessToken())).orElseThrow();

        mvc.perform(delete("/api/profile/sessions/" + token.getId())
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/profile/sessions/" + token.getId())
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(tokens.findById(token.getId()).orElseThrow().isRevoked());
    }

    @Test
    void foreignSessionLooksLikeOrdinaryNotFoundAndDeleteRequiresCsrf() throws Exception {
        MobileTokenResponse issued = mobileAuthService.issueTokenPairForRegisteredUser(other, "Foreign phone");
        MobileAuthToken token = tokens.findByAccessTokenHash(MobileAuthService.hash(issued.accessToken())).orElseThrow();

        mvc.perform(delete("/api/profile/sessions/" + token.getId())
                        .with(user(owner.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/profile/sessions/" + token.getId())
                        .with(user(other.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeRevokesEveryMobileSessionButLeavesRowsForDeviceHistory() throws Exception {
        mobileAuthService.issueTokenPairForRegisteredUser(owner, "Phone");
        mobileAuthService.issueTokenPairForRegisteredUser(owner, "Tablet");

        mvc.perform(post("/api/profile/password")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isNoContent());

        List<MobileAuthToken> stored = tokens.findByOwnerOrderByCreatedAtDesc(owner);
        assertTrue(stored.size() == 2 && stored.stream().allMatch(MobileAuthToken::isRevoked));
        assertTrue(encoder.matches("new-password", users.findById(owner.getId()).orElseThrow().getPasswordHash()));
    }
}
