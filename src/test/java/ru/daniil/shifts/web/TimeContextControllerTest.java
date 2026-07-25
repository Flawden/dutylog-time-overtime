package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimeContextControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = new AppUser("time-context-owner", "{noop}unused");
        owner.setWorkTimezone("Europe/Chisinau");
        owner.setDisplayTimezone("Europe/Berlin");
        owner = users.save(owner);
    }

    @Test
    void legacyAndV1ExposeOneInstantWithTwoExplicitProjections() throws Exception {
        assertContext("/api/time/context");
        assertContext("/api/v1/time/context");
    }

    private void assertContext(String path) throws Exception {
        mvc.perform(get(path).with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nowInstant").isString())
                .andExpect(jsonPath("$.workTimezone").value("Europe/Chisinau"))
                .andExpect(jsonPath("$.displayTimezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.workLocalDateTime").isString())
                .andExpect(jsonPath("$.displayLocalDateTime").isString())
                .andExpect(jsonPath("$.workDate").isString())
                .andExpect(jsonPath("$.displayDate").isString())
                .andExpect(jsonPath("$.workOffset").isString())
                .andExpect(jsonPath("$.displayOffset").isString())
                .andExpect(jsonPath("$.sameTimezone").value(false));
    }
}
