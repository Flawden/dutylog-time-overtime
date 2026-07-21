package ru.daniil.shifts.web;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Browser persistence contract for the explicit "remember this device" login option. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RememberMeAuthenticationTest {
    private static final String USERNAME = "remember-browser-user";
    private static final String PASSWORD = "remember-browser-password";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users.save(new AppUser(USERNAME, encoder.encode(PASSWORD)));
    }

    @Test
    void rememberedLoginSurvivesWithoutTheOriginalHttpSession() throws Exception {
        MvcResult login = mvc.perform(post("/perform_login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .param("remember-me", "on"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie rememberMe = login.getResponse().getCookie("DUTYLOG_REMEMBER_ME");
        assertNotNull(rememberMe, "remember-me login must issue a persistent cookie");
        assertTrue(rememberMe.getMaxAge() > 0, "remember-me cookie must outlive the browser session");
        assertTrue(rememberMe.isHttpOnly(), "remember-me cookie must not be readable by JavaScript");

        // Deliberately do not pass the JSESSIONID from the login response. The
        // persistent token alone must rebuild authentication on a new request.
        mvc.perform(get("/api/profile").cookie(rememberMe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void theSameRememberCookieCanBootstrapParallelPwaRequests() throws Exception {
        MvcResult login = mvc.perform(post("/perform_login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .param("remember-me", "on"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Cookie rememberMe = login.getResponse().getCookie("DUTYLOG_REMEMBER_ME");
        assertNotNull(rememberMe);

        // A restored PWA fires several requests before any rotated cookie could
        // be observed. Both requests therefore intentionally carry the same
        // original remember-me cookie.
        mvc.perform(get("/api/profile").cookie(rememberMe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));
        mvc.perform(get("/api/modules").cookie(rememberMe))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryLoginDoesNotCreateAPersistentCookie() throws Exception {
        MvcResult login = mvc.perform(post("/perform_login")
                        .with(csrf())
                        .param("username", USERNAME)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertNull(login.getResponse().getCookie("DUTYLOG_REMEMBER_ME"));
    }
}
