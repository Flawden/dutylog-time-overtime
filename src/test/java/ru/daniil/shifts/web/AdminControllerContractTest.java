package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full administrator HTTP contract beyond the basic access-control smoke tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerContractTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MobileAuthService mobileAuthService;
    @Autowired MobileAuthTokenRepository tokens;

    AppUser admin;
    AppUser secondAdmin;
    AppUser regular;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("admin-contract-root");
        secondAdmin = saveAdmin("admin-contract-second");
        regular = users.save(new AppUser("admin-contract-regular", encoder.encode("old-password")));
        regular.setDisplayName("Searchable Operator");
        regular = users.save(regular);
    }

    private AppUser saveAdmin(String username) {
        AppUser value = new AppUser(username, encoder.encode("admin-password"));
        value.setRole("ADMIN");
        return users.save(value);
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getUsername()).roles("ADMIN", "USER");
    }

    @Test
    void statusContainsOperationalFactsButNeverSecretsOrDatabaseUrls() throws Exception {
        mvc.perform(get("/api/admin/status").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value("DutyLog: Time & Overtime"))
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.admin").value(admin.getUsername()))
                .andExpect(jsonPath("$.database.ok").value(true))
                .andExpect(jsonPath("$.users.total").value(3))
                .andExpect(jsonPath("$.users.admins").value(2))
                .andExpect(jsonPath("$.users.rolesAllowed", hasItem("ADMIN")))
                .andExpect(jsonPath("$.registration.enabled").value(true))
                .andExpect(jsonPath("$.telegram.tokenConfigured").value(false))
                .andExpect(jsonPath("$.telegram.botToken").doesNotExist())
                .andExpect(jsonPath("$.database.url").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void usersEndpointSupportsSearchRoleAndPaginationMetadata() throws Exception {
        for (int i = 0; i < 12; i++) {
            users.save(new AppUser("admin-page-" + i, encoder.encode("unused-password")));
        }

        mvc.perform(get("/api/admin/users")
                        .param("q", "Searchable")
                        .param("role", "user")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value(regular.getUsername()))
                .andExpect(jsonPath("$.items[0].currentUser").value(false));

        mvc.perform(get("/api/admin/users")
                        .param("page", "1").param("size", "1")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasPrevious").value(true));
    }

    @Test
    void roleChangesWorkAndSafetyFailuresUseStableBadRequestEnvelope() throws Exception {
        mvc.perform(patch("/api/admin/users/" + regular.getId() + "/role")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(patch("/api/admin/users/" + admin.getId() + "/role")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(patch("/api/admin/users/" + secondAdmin.getId() + "/role")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void passwordResetRequiresTwelveCharactersHashesValueAndRevokesSessions() throws Exception {
        mobileAuthService.issueTokenPairForRegisteredUser(regular, "Phone");
        mobileAuthService.issueTokenPairForRegisteredUser(regular, "Tablet");

        mvc.perform(post("/api/admin/users/" + regular.getId() + "/password")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"newPassword\":\"too-short\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/users/" + regular.getId() + "/password")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"newPassword\":\"long-enough-reset-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(regular.getUsername()));

        AppUser stored = users.findById(regular.getId()).orElseThrow();
        assertTrue(encoder.matches("long-enough-reset-password", stored.getPasswordHash()));
        List<MobileAuthToken> sessions = tokens.findByOwnerOrderByCreatedAtDesc(stored);
        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().allMatch(MobileAuthToken::isRevoked));
    }

    @Test
    void registrationSwitchPersistsAuditMetadataAndPublicStatusChangesImmediately() throws Exception {
        mvc.perform(patch("/api/admin/settings/registration")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.mode").value("closed"))
                .andExpect(jsonPath("$.source").value("database"))
                .andExpect(jsonPath("$.updatedBy").value(admin.getUsername()))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void allAdministrativeWritesRequireCsrf() throws Exception {
        mvc.perform(patch("/api/admin/users/" + regular.getId() + "/role")
                        .with(asAdmin())
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/" + regular.getId() + "/password")
                        .with(asAdmin())
                        .contentType("application/json").content("{\"newPassword\":\"long-enough-password\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/settings/registration")
                        .with(asAdmin())
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedBodiesAndMissingUsersNeverBecomeServerErrors() throws Exception {
        mvc.perform(patch("/api/admin/users/" + regular.getId() + "/role")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/users/" + Long.MAX_VALUE + "/password")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{\"newPassword\":\"long-enough-password\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(patch("/api/admin/settings/registration")
                        .with(asAdmin()).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
