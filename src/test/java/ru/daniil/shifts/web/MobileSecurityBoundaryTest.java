package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves that /api/mobile/** never falls back to a browser JSESSIONID. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileSecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired PasswordEncoder passwordEncoder;

    AppUser user;
    String accessToken;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("mobile-boundary", passwordEncoder.encode("secret123")));
        accessToken = "mobile-boundary-access";
        tokens.saveAndFlush(new MobileAuthToken(
                user,
                MobileAuthService.hash(accessToken),
                MobileAuthService.hash("mobile-boundary-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "boundary-test"));
    }

    @Test
    void webSessionCannotAuthenticateMobileApi() throws Exception {
        MvcResult login = mvc.perform(post("/perform_login")
                        .with(csrf())
                        .param("username", "mobile-boundary")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession webSession = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/mobile/auth/me").session(webSession))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/mobile/sync")
                        .session(webSession)
                        .contentType("application/json")
                        .content("{\"days\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerAuthenticatesMobileApi() throws Exception {
        mvc.perform(get("/api/mobile/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"days\":[]}"))
                .andExpect(status().isOk());
    }


    @Test
    void bearerCanUseSharedApiWithoutBrowserCsrf() throws Exception {
        mvc.perform(patch("/api/modules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"enabled\":{\"tasks\":false}}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidBearerIsRejected() throws Exception {
        mvc.perform(get("/api/mobile/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
