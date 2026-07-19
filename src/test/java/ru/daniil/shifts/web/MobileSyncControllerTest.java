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
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.MobileAuthTokenRepository;
import ru.daniil.shifts.repo.MobileSyncOperationRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.MobileAuthService;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Additional HTTP contracts for Android v1 batch isolation and legacy compatibility. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileSyncControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MobileAuthTokenRepository tokens;
    @Autowired MobileSyncOperationRepository operations;
    @Autowired DayEntryRepository days;
    @Autowired PasswordEncoder encoder;

    AppUser owner;
    String accessToken;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("mobile-sync-http-owner", encoder.encode("secret123")));
        accessToken = "mobile-sync-http-access";
        tokens.saveAndFlush(new MobileAuthToken(
                owner,
                MobileAuthService.hash(accessToken),
                MobileAuthService.hash("mobile-sync-http-refresh"),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "HTTP sync test"));
    }

    @Test
    void malformedDateRejectsOnlyThatItemAndValidNeighbourStillCommits() throws Exception {
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[
                                    {
                                      "operationId":"http-bad-date",
                                      "baseVersion":0,
                                      "day":{"date":"not-a-date","note":"blocked"}
                                    },
                                    {
                                      "operationId":"http-good-neighbour",
                                      "baseVersion":0,
                                      "day":{"date":"2026-09-01","note":"saved"}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.items[0].entityId").value("not-a-date"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(nullValue()))
                .andExpect(jsonPath("$.items[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[1].serverVersion").value(1));

        org.junit.jupiter.api.Assertions.assertEquals("saved",
                days.findByOwnerAndDate(owner, LocalDate.parse("2026-09-01")).orElseThrow().getNote());
        org.junit.jupiter.api.Assertions.assertEquals(2, operations.count());
    }

    @Test
    void duplicateOperationIdInsideOneBatchAppliesOnlyOnce() throws Exception {
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[
                                    {
                                      "operationId":"same-batch-id",
                                      "baseVersion":0,
                                      "day":{"date":"2026-09-02","note":"first wins"}
                                    },
                                    {
                                      "operationId":"same-batch-id",
                                      "baseVersion":0,
                                      "day":{"date":"2026-09-02","note":"must be ignored"}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[1].status").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(1))
                .andExpect(jsonPath("$.items[1].serverVersion").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(1, operations.count());
        org.junit.jupiter.api.Assertions.assertEquals("first wins",
                days.findByOwnerAndDate(owner, LocalDate.parse("2026-09-02")).orElseThrow().getNote());
    }

    @Test
    void beanValidationRejectsMalformedOperationShapeBeforeServiceExecution() throws Exception {
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {
                                  "operations":[
                                    {
                                      "operationId":"spaces are forbidden",
                                      "baseVersion":-1,
                                      "day":{"date":"2026-09-03","note":"x"}
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields").isMap());

        org.junit.jupiter.api.Assertions.assertEquals(0, operations.count());
        org.junit.jupiter.api.Assertions.assertTrue(days.findByOwnerAndDate(
                owner, LocalDate.parse("2026-09-03")).isEmpty());
    }

    @Test
    void legacyClearDeletesEmptyRowWhileV1ClearKeepsVersionedTombstone() throws Exception {
        mvc.perform(post("/api/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("{\"days\":[{\"date\":\"2026-09-04\",\"note\":\"legacy\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].note").value("legacy"));
        mvc.perform(post("/api/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("{\"days\":[{\"date\":\"2026-09-04\",\"clearNote\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(days.findByOwnerAndDate(
                owner, LocalDate.parse("2026-09-04")).isEmpty());

        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {"operations":[{
                                  "operationId":"v1-tombstone-create",
                                  "baseVersion":0,
                                  "day":{"date":"2026-09-05","note":"v1"}
                                }]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serverVersion").value(1));
        mvc.perform(post("/api/v1/mobile/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType("application/json")
                        .content("""
                                {"operations":[{
                                  "operationId":"v1-tombstone-clear",
                                  "baseVersion":1,
                                  "day":{"date":"2026-09-05","clearNote":true}
                                }]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.items[0].serverVersion").value(2))
                .andExpect(jsonPath("$.items[0].entity.note").value(nullValue()));

        var tombstone = days.findByOwnerAndDate(owner, LocalDate.parse("2026-09-05")).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(tombstone.isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(2L, tombstone.getSyncVersion());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
