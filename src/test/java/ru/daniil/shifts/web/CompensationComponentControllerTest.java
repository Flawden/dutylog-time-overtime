package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompensationComponentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    CompensationComponentRepository components;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.saveAndFlush(
                        new AppUser(
                                "component-api-"
                                        + UUID.randomUUID()
                                                .toString()
                                                .substring(
                                                        0,
                                                        12
                                                ),
                                "{noop}unused"
                        )
                );
    }

    @Test
    void v1CreateVersionHistoryAndEffectiveReadUseNativeBoundary()
            throws Exception {

        mvc.perform(
                        post("/api/v1/payroll/compensation-components")
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "effectiveMonth":"2026-08",
                                          "version":{
                                            "displayName":"Премия за выживание после ночной смены",
                                            "earningKind":"HARMFUL_CONDITIONS",
                                            "calculationType":"PERCENT_OF_BASE",
                                            "calculationBase":"EARNED_BASE_PAY",
                                            "rateBps":400,
                                            "amountMinor":null,
                                            "currencyCode":null,
                                            "enabled":true
                                          }
                                        }
                                        """)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.effectiveMonth")
                                .value("2026-08")
                )
                .andExpect(
                        jsonPath("$.displayName")
                                .value(
                                        "Премия за выживание после ночной смены"
                                )
                )
                .andExpect(
                        jsonPath("$.earningKind")
                                .value(
                                        "HARMFUL_CONDITIONS"
                                )
                )
                .andExpect(
                        jsonPath("$.rateBps")
                                .value(400)
                );

        Long componentId =
                components
                        .findByOwnerOrderByIdAsc(
                                owner
                        )
                        .get(0)
                        .getId();

        mvc.perform(
                        put(
                                "/api/v1/payroll/compensation-components/{componentId}/versions/{month}",
                                componentId,
                                "2026-09"
                        )
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "displayName":"Премия за выживание после ночной смены",
                                          "calculationType":"PERCENT_OF_BASE",
                                          "calculationBase":"EARNED_BASE_PAY",
                                          "rateBps":600,
                                          "amountMinor":null,
                                          "currencyCode":null,
                                          "enabled":true
                                        }
                                        """)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.componentId")
                                .value(componentId)
                )
                .andExpect(
                        jsonPath("$.effectiveMonth")
                                .value("2026-09")
                )
                .andExpect(
                        jsonPath("$.earningKind")
                                .value(
                                        "HARMFUL_CONDITIONS"
                                )
                )
                .andExpect(
                        jsonPath("$.rateBps")
                                .value(600)
                );

        mvc.perform(
                        get(
                                "/api/v1/payroll/compensation-components/effective/{month}",
                                "2026-10"
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
                        header().string(
                                "Cache-Control",
                                containsString(
                                        "no-store"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$[0].componentId")
                                .value(componentId)
                )
                .andExpect(
                        jsonPath("$[0].rateBps")
                                .value(600)
                );

        mvc.perform(
                        get("/api/v1/payroll/compensation-components")
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
                        header().string(
                                "Cache-Control",
                                containsString(
                                        "no-store"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                );
    }

    @Test
    void compatibilityAliasUsesSameComponentBoundary()
            throws Exception {

        mvc.perform(
                        post("/api/payroll/compensation-components")
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "effectiveMonth":"2026-08",
                                          "version":{
                                            "displayName":"За классность",
                                            "calculationType":"FIXED_AMOUNT",
                                            "calculationBase":null,
                                            "rateBps":null,
                                            "amountMinor":10000,
                                            "currencyCode":"RUB",
                                            "enabled":true
                                          }
                                        }
                                        """)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.displayName")
                                .value(
                                        "За классность"
                                )
                );

        mvc.perform(
                        get(
                                "/api/payroll/compensation-components/effective/2026-08"
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
                        jsonPath("$[0].amountMinor")
                                .value(10000)
                );
    }

    @Test
    void componentWritesRemainCsrfProtected()
            throws Exception {

        mvc.perform(
                        post("/api/v1/payroll/compensation-components")
                                .with(
                                        user(
                                                owner.getUsername()
                                        ).roles("USER")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "effectiveMonth":"2026-08",
                                          "version":{
                                            "displayName":"CSRF",
                                            "calculationType":"FIXED_AMOUNT",
                                            "amountMinor":10000,
                                            "currencyCode":"RUB",
                                            "enabled":true
                                          }
                                        }
                                        """)
                )
                .andExpect(
                        status().isForbidden()
                );
    }
}
