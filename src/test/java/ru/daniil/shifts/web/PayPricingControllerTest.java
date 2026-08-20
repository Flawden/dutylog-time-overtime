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
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayPricingControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    AppUser owner;

    @BeforeEach
    void setUp() {
        String suffix =
                UUID.randomUUID()
                        .toString()
                        .substring(0, 12);

        owner = users.saveAndFlush(
                new AppUser(
                        "pricing-api-" + suffix,
                        "{noop}unused"
                )
        );
    }

    @Test
    void v1CrudPersistsWholeEffectiveDatedTerm() throws Exception {
        LocalDate effective = LocalDate.now().plusYears(1);

        mvc.perform(
                        put("/api/v1/payroll/pricing/terms/{effectiveFrom}", effective)
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rules":[
                                            {
                                              "code":"NIGHT",
                                              "dimension":"NIGHT",
                                              "premiumBps":2000,
                                              "fromMinute":0,
                                              "toMinuteExclusive":null,
                                              "exclusiveGroup":null
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value(effective.toString()))
                .andExpect(jsonPath("$.rules[0].code").value("NIGHT"))
                .andExpect(jsonPath("$.rules[0].premiumBps").value(2000));

        mvc.perform(
                        get("/api/v1/payroll/pricing/terms")
                                .with(user(owner.getUsername()).roles("USER"))
                )
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$[0].effectiveFrom").value(effective.toString()));

        mvc.perform(
                        delete("/api/v1/payroll/pricing/terms/{effectiveFrom}", effective)
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        mvc.perform(
                        get("/api/v1/payroll/pricing/terms")
                                .with(user(owner.getUsername()).roles("USER"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void compatibilityAliasUsesSameConfigurationBoundary() throws Exception {
        mvc.perform(
                        put("/api/payroll/pricing/terms/2026-08-20")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rules\":[]}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom").value("2026-08-20"))
                .andExpect(jsonPath("$.rules").isEmpty());

        mvc.perform(
                        get("/api/payroll/pricing/terms")
                                .with(user(owner.getUsername()).roles("USER"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-08-20"));
    }

    @Test
    void invalidRuleShapeReturnsStableApiCode() throws Exception {
        mvc.perform(
                        put("/api/v1/payroll/pricing/terms/2026-08-20")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rules":[
                                            {
                                              "code":"BAD",
                                              "dimension":"NIGHT",
                                              "premiumBps":2000,
                                              "fromMinute":60
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY_PRICING_INVALID"));
    }

    @Test
    void beanValidationRejectsUnsupportedDimension() throws Exception {
        mvc.perform(
                        put("/api/v1/payroll/pricing/terms/2026-08-20")
                                .with(user(owner.getUsername()).roles("USER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rules":[
                                            {
                                              "code":"BONUS",
                                              "dimension":"BONUS",
                                              "premiumBps":2000,
                                              "fromMinute":0
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }
}
