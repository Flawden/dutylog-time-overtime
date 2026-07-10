package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.MobileAuthToken;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.AppSettingsService;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract tests for the stable Android /api/v1 surface. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileV1ContractTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AppSettingsService settings;
    @Autowired ModuleService moduleService;

    private String accessToken;
    private AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("android-v1-user", passwordEncoder.encode("secret123")));
        accessToken = "android-v1-access";
        tokens.saveAndFlush(new MobileAuthToken(
                user,
                MobileAuthService.hash(accessToken),
                MobileAuthService.hash("android-v1-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "Android contract test"));
    }

    @Test
    void unauthenticatedV1RequestUsesMachineReadableErrorEnvelope() throws Exception {
        mvc.perform(get("/api/v1/mobile/bootstrap")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-DutyLog-Api-Version", "v1"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mobileRegistrationCreatesUserAndImmediatelyReturnsTokens() throws Exception {
        settings.setRegistrationEnabled(true, "test");

        mvc.perform(post("/api/v1/mobile/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"android-new-user",
                                  "password":"secret123",
                                  "languagePreference":"en",
                                  "deviceName":"Pixel test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-DutyLog-Api-Version", "v1"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("android-new-user"));

        org.junit.jupiter.api.Assertions.assertEquals(
                "en", users.findByUsername("android-new-user").orElseThrow().getLanguagePreference());
    }

    @Test
    void mobileRegistrationRespectsServerRegistrationSwitch() throws Exception {
        settings.setRegistrationEnabled(false, "test");

        mvc.perform(post("/api/v1/mobile/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"registration-must-stay-closed",
                                  "password":"secret123",
                                  "languagePreference":"ru",
                                  "deviceName":"blocked test"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));
    }

    @Test
    void invalidMobileLoginReturnsUnauthorizedMachineCode() throws Exception {
        mvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"android-v1-user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void openApiContractIsPubliclyAvailable() throws Exception {
        mvc.perform(get("/openapi/dutylog-v1.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void bootstrapAndSharedV1AliasesAcceptBearer() throws Exception {
        mvc.perform(get("/api/v1/mobile/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-DutyLog-Api-Version", "v1"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.user.username").value("android-v1-user"));

        mvc.perform(get("/api/v1/modules")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-DutyLog-Api-Version", "v1"));
    }

    @Test
    void syncIsIdempotentAndReturnsPerOperationConflict() throws Exception {
        String operation = """
                {
                  "operations":[{
                    "operationId":"op-android-001",
                    "baseVersion":0,
                    "day":{"date":"2026-07-10","note":"first mobile note"}
                  }]
                }
                """;

        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content(operation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[0].entity.note").value("first mobile note"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(1));

        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content(operation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(1));

        // A second client that still believes the row is absent (version 0)
        // must conflict after the first create; otherwise the initial write can be lost.
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[{
                                    "operationId":"op-android-stale-absent",
                                    "baseVersion":0,
                                    "day":{"date":"2026-07-10","note":"must conflict"}
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(1))
                .andExpect(jsonPath("$.items[0].errorCode").value("VERSION_CONFLICT"));

        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[{
                                    "operationId":"op-android-002",
                                    "baseVersion":999,
                                    "day":{"date":"2026-07-10","note":"stale write"}
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.items[0].errorCode").value("VERSION_CONFLICT"));
    }

    @Test
    void oneRejectedSyncItemDoesNotBlockNeighbouringOperation() throws Exception {
        moduleService.update(user, new ModuleSettingsUpdateRequest(Map.of("notes", false)));

        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[
                                    {
                                      "operationId":"op-disabled-note",
                                      "baseVersion":0,
                                      "day":{"date":"2026-07-11","note":"must be rejected"}
                                    },
                                    {
                                      "operationId":"op-allowed-emoji",
                                      "baseVersion":0,
                                      "day":{"date":"2026-07-12","dayEmoji":"✅"}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].errorCode").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.items[1].status").value("APPLIED"));
    }

    @Test
    void noOpSyncItemIsRejectedWithoutCreatingARecord() throws Exception {
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[{
                                    "operationId":"op-no-changes",
                                    "baseVersion":0,
                                    "day":{"date":"2026-07-13"}
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].errorCode").value("NO_CHANGES"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(0));
    }

    @Test
    void invalidV1PayloadUsesValidationCodeAndFields() throws Exception {
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {"operations":[{"baseVersion":0,"day":{"date":"2026-07-10"}}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields").isMap());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
