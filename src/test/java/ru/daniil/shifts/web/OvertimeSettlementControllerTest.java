package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OvertimeSettlementControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner =
                users.save(
                        new AppUser(
                                "settlement-api-owner",
                                "{noop}unused"
                        )
                );

        other =
                users.save(
                        new AppUser(
                                "settlement-api-other",
                                "{noop}unused"
                        )
                );
    }

    @Test
    void publicSettlementCrudConsumesBankWithoutMasqueradingAsTimeOff()
            throws Exception {

        setOvertimeEnabled(
                owner,
                true
        );

        createCredit(
                owner,
                "2026-08-01",
                2.0
        );

        String createdJson =
                mvc.perform(
                                post(
                                        "/api/v1/overtime/settlements"
                                )
                                        .with(
                                                user(
                                                        owner.getUsername()
                                                ).roles("USER")
                                        )
                                        .with(csrf())
                                        .contentType(
                                                "application/json"
                                        )
                                        .content("""
                                                {
                                                  "settlementDate":"2026-08-03",
                                                  "minutes":60,
                                                  "reason":"Оплатить час"
                                                }
                                                """)
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath(
                                        "$.settlementDate"
                                ).value(
                                        "2026-08-03"
                                )
                        )
                        .andExpect(
                                jsonPath(
                                        "$.minutes"
                                ).value(60)
                        )
                        .andExpect(
                                jsonPath(
                                        "$.hours"
                                ).value(1.0)
                        )
                        .andExpect(
                                jsonPath(
                                        "$.reason"
                                ).value(
                                        "Оплатить час"
                                )
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long settlementId =
                objectMapper
                        .readTree(createdJson)
                        .path("id")
                        .asLong();

        assertTrue(
                settlementId > 0
        );

        mvc.perform(
                        get(
                                "/api/v1/overtime/settlements"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.length()"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$[0].id"
                        ).value(
                                settlementId
                        )
                );

        mvc.perform(
                        get(
                                "/api/v1/overtime/account"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalEarnedHours"
                        ).value(2.0)
                )
                .andExpect(
                        jsonPath(
                                "$.totalUsedHours"
                        ).value(1.0)
                )
                .andExpect(
                        jsonPath(
                                "$.balanceHours"
                        ).value(1.0)
                )
                .andExpect(
                        jsonPath(
                                "$.usages[0].sourceKind"
                        ).value(
                                "SETTLEMENT"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.usages[0].sourceSettlementId"
                        ).value(
                                settlementId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.usages[0].sourceAbsenceId"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.usages[0].editable"
                        ).value(false)
                );

        /*
         * Compatibility timeOff must not become synonymous with every debit.
         * Balance still includes the settlement.
         */
        mvc.perform(
                        get(
                                "/api/v1/overtime/balance"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .param(
                                        "from",
                                        "2026-08-01"
                                )
                                .param(
                                        "to",
                                        "2026-08-01"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.overtimeHours"
                        ).value(2.0)
                )
                .andExpect(
                        jsonPath(
                                "$.timeOffHours"
                        ).value(0.0)
                )
                .andExpect(
                        jsonPath(
                                "$.balanceHours"
                        ).value(1.0)
                );

        byte[] csv =
                mvc.perform(
                                get(
                                        "/api/v1/overtime/export.csv"
                                )
                                        .with(
                                                user(
                                                        owner.getUsername()
                                                ).roles("USER")
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        String csvText =
                new String(
                        csv,
                        StandardCharsets.UTF_8
                );

        assertTrue(
                csvText.contains(
                        "к оплате"
                ),
                "export must explain settlement instead of calling it time-off"
        );

        mvc.perform(
                        patch(
                                "/api/v1/overtime/settlements/{id}",
                                settlementId
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "settlementDate":"2026-08-04",
                                          "minutes":30,
                                          "reason":"Оплатить полчаса"
                                        }
                                        """)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.minutes"
                        ).value(30)
                )
                .andExpect(
                        jsonPath(
                                "$.hours"
                        ).value(0.5)
                );

        mvc.perform(
                        get(
                                "/api/v1/overtime/account"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalUsedHours"
                        ).value(0.5)
                )
                .andExpect(
                        jsonPath(
                                "$.balanceHours"
                        ).value(1.5)
                );

        mvc.perform(
                        delete(
                                "/api/v1/overtime/settlements/{id}",
                                settlementId
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                )
                .andExpect(
                        status().isNoContent()
                );

        mvc.perform(
                        get(
                                "/api/v1/overtime/account"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalUsedHours"
                        ).value(0.0)
                )
                .andExpect(
                        jsonPath(
                                "$.balanceHours"
                        ).value(2.0)
                )
                .andExpect(
                        jsonPath(
                                "$.usages.length()"
                        ).value(0)
                );
    }

    @Test
    void settlementEndpointsPreserveOwnershipAndCsrf()
            throws Exception {

        setOvertimeEnabled(
                owner,
                true
        );

        setOvertimeEnabled(
                other,
                true
        );

        createCredit(
                other,
                "2026-08-01",
                2.0
        );

        String foreignJson =
                mvc.perform(
                                post(
                                        "/api/v1/overtime/settlements"
                                )
                                        .with(
                                                user(
                                                        other.getUsername()
                                                ).roles("USER")
                                        )
                                        .with(csrf())
                                        .contentType(
                                                "application/json"
                                        )
                                        .content("""
                                                {
                                                  "settlementDate":"2026-08-03",
                                                  "minutes":60
                                                }
                                                """)
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        long foreignId =
                objectMapper
                        .readTree(foreignJson)
                        .path("id")
                        .asLong();

        mvc.perform(
                        get(
                                "/api/v1/overtime/settlements"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.length()"
                        ).value(0)
                );

        mvc.perform(
                        patch(
                                "/api/v1/overtime/settlements/{id}",
                                foreignId
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "settlementDate":"2026-08-04",
                                          "minutes":30
                                        }
                                        """)
                )
                .andExpect(
                        status().isNotFound()
                );

        mvc.perform(
                        delete(
                                "/api/v1/overtime/settlements/{id}",
                                foreignId
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                )
                .andExpect(
                        status().isNotFound()
                );

        mvc.perform(
                        post(
                                "/api/v1/overtime/settlements"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content("""
                                        {
                                          "settlementDate":"2026-08-03",
                                          "minutes":30
                                        }
                                        """)
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    private void createCredit(
            AppUser account,
            String date,
            double hours
    ) throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/overtime/credits"
                        )
                                .with(
                                        user(
                                                account.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        "application/json"
                                )
                                .content(
                                        """
                                        {
                                          "date":"%s",
                                          "hours":%s,
                                          "reason":"settlement API proof"
                                        }
                                        """.formatted(
                                                date,
                                                hours
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    private void setOvertimeEnabled(
            AppUser account,
            boolean enabled
    ) throws Exception {

        mvc.perform(
                        patch(
                                "/api/modules"
                        )
                                .with(
                                        user(
                                                account.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        "application/json"
                                )
                                .content(
                                        "{\"enabled\":{\"overtime\":"
                                                + enabled
                                                + "}}"
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }
}
