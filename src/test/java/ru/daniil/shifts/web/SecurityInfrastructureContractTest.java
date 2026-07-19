package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityInfrastructureContractTest {
    @Autowired MockMvc mvc;

    @Test
    void publicLoginPageCarriesDefenseInDepthHeadersAndProxyHttpsAddsHsts() throws Exception {
        mvc.perform(get("/login.html").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), microphone=(), camera=()"))
                .andExpect(header().string("Content-Security-Policy", matchesPattern(".*default-src 'self'.*")))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"));
    }

    @Test
    void stableAndLegacyMobileRoutesExposeVersionLifecycleHeadersEvenWhenUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/mobile/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-DutyLog-Api-Version", "v1"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mvc.perform(get("/api/mobile/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(header().string("Link", "</api/v1/mobile>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void unauthenticatedWebApiUsesJson401AndEchoesSafeCallerRequestId() throws Exception {
        mvc.perform(get("/api/calendar").header("X-Request-Id", "client.req-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "client.req-123"))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.requestId").value("client.req-123"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "regular", roles = "USER")
    void nonAdministratorReceivesStableJson403ForAdminApi() throws Exception {
        mvc.perform(get("/api/admin/status").header("X-Request-Id", "admin-denied-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("admin-denied-1"));
    }

    @Test
    void mixedCaseBearerSchemeIsRecognizedInsteadOfFallingThroughAsAnonymous() throws Exception {
        mvc.perform(get("/api/calendar")
                        .header("Authorization", "bEaReR definitely-invalid")
                        .header("X-Request-Id", "bearer-case-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").value("bearer-case-1"));
    }

    @Test
    void browserPageRedirectAndMalformedRegistrationJsonStayOnTheirExpectedChannels() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login.html"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isForbidden());
    }
}
