package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.MobileTokenResponse;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP lifecycle contract for both legacy and stable Android auth routes. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileAuthLifecycleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired MobileAuthService mobileAuthService;
    @Autowired PasswordEncoder encoder;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("mobile-http-owner", encoder.encode("secret123")));
        other = users.save(new AppUser("mobile-http-other", encoder.encode("other123")));
    }

    @Test
    void legacyAndV1LoginReturnBearerPairsWithoutPersistingRawTokens() throws Exception {
        for (String path : List.of("/api/mobile/auth/login", "/api/v1/mobile/auth/login")) {
            MvcResult result = mvc.perform(post(path)
                            .contentType("application/json")
                            .content("""
                                    {"username":"mobile-http-owner","password":"secret123","deviceName":"Pixel"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.username").value(owner.getUsername()))
                    .andExpect(jsonPath("$.accessToken").isString())
                    .andExpect(jsonPath("$.refreshToken").isString())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            String access = body.get("accessToken").asText();
            String refresh = body.get("refreshToken").asText();
            MobileAuthToken stored = tokens.findByAccessTokenHash(MobileAuthService.hash(access)).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(MobileAuthService.hash(refresh), stored.getRefreshTokenHash());
            org.junit.jupiter.api.Assertions.assertNotEquals(access, stored.getAccessTokenHash());
            org.junit.jupiter.api.Assertions.assertNotEquals(refresh, stored.getRefreshTokenHash());
        }
    }

    @Test
    void refreshRotationInvalidatesOldAccessAndRefreshOverHttp() throws Exception {
        JsonNode first = login("/api/v1/mobile/auth/login", owner.getUsername(), "secret123", "Phone");
        String oldAccess = first.get("accessToken").asText();
        String oldRefresh = first.get("refreshToken").asText();

        MvcResult refreshResult = mvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        JsonNode rotated = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccess = rotated.get("accessToken").asText();
        String newRefresh = rotated.get("refreshToken").asText();

        mvc.perform(get("/api/v1/mobile/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(oldAccess)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(get("/api/v1/mobile/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccess)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(owner.getUsername()));
        mvc.perform(post("/api/mobile/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        mvc.perform(post("/api/mobile/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutByRefreshRevokesTheWholePairAndIsIdempotent() throws Exception {
        JsonNode pair = login("/api/mobile/auth/login", owner.getUsername(), "secret123", "Phone");
        String access = pair.get("accessToken").asText();
        String refresh = pair.get("refreshToken").asText();
        String payload = "{\"refreshToken\":\"" + refresh + "\"}";

        mvc.perform(post("/api/v1/mobile/auth/logout")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/mobile/auth/logout")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/mobile/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void logoutByBearerWorksWithAnEmptyBodyBecauseLogoutRouteIsPublic() throws Exception {
        JsonNode pair = login("/api/v1/mobile/auth/login", owner.getUsername(), "secret123", "Phone");
        String access = pair.get("accessToken").asText();

        mvc.perform(post("/api/v1/mobile/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(access))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/mobile/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(access)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void bearerSessionsAreOwnerScopedAndCanBeRevokedWithoutBrowserCsrf() throws Exception {
        MobileTokenResponse primary = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Primary");
        MobileTokenResponse secondary = mobileAuthService.issueTokenPairForRegisteredUser(owner, "Secondary");
        MobileTokenResponse foreign = mobileAuthService.issueTokenPairForRegisteredUser(other, "Foreign");
        MobileAuthToken secondaryRow = tokens.findByAccessTokenHash(MobileAuthService.hash(secondary.accessToken()))
                .orElseThrow();
        MobileAuthToken foreignRow = tokens.findByAccessTokenHash(MobileAuthService.hash(foreign.accessToken()))
                .orElseThrow();

        mvc.perform(get("/api/v1/mobile/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].deviceName", hasItems("Primary", "Secondary")))
                .andExpect(jsonPath("$[*].deviceName", not(hasItem("Foreign"))))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist());

        mvc.perform(delete("/api/v1/mobile/auth/sessions/" + foreignRow.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/mobile/auth/sessions/" + secondaryRow.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary.accessToken())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/mobile/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.accessToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAuthBodiesUseStableClientErrorsAndAnonymousLogoutIsSafe() throws Exception {
        mvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields").isMap());

        mvc.perform(post("/api/mobile/auth/login")
                        .contentType("application/json")
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/mobile/auth/logout")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNoContent());
    }

    private JsonNode login(String path, String username, String password, String device) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                                + "\",\"deviceName\":\"" + device + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
