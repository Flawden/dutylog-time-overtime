package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.UserAdminService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end security-chain proof that stale JSESSIONID authorities cannot survive account changes. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebSessionInvalidationTest {
    private static final String PASSWORD = "old-web-session-pass";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired UserAdminService userAdminService;

    AppUser actingAdmin;
    AppUser targetAdmin;
    AppUser regular;

    @BeforeEach
    void setUp() {
        actingAdmin = admin("session-acting-admin");
        targetAdmin = admin("session-target-admin");
        regular = users.save(new AppUser("session-regular", encoder.encode(PASSWORD)));
    }

    @Test
    void passwordChangeInvalidatesTheExistingBrowserSessionOnNextRequest() throws Exception {
        MockHttpSession session = login("session-regular", PASSWORD);

        mvc.perform(post("/api/profile/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"new-web-session-pass\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/profile")
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleDemotionInvalidatesCachedAdminAuthoritiesOnNextRequest() throws Exception {
        MockHttpSession session = login("session-target-admin", PASSWORD);
        mvc.perform(get("/api/admin/status").session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        userAdminService.changeRole(targetAdmin.getId(), "USER", actingAdmin);

        mvc.perform(get("/api/admin/status")
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private AppUser admin(String username) {
        AppUser user = new AppUser(username, encoder.encode(PASSWORD));
        user.setRole("ADMIN");
        return users.save(user);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/perform_login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
