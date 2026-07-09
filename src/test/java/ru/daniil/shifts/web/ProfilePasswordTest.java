package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Смена пароля — операция владения аккаунтом. Контракты:
 * без верного текущего пароля не меняется; новый реально применяется;
 * к админам требования строже (12 символов против 6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfilePasswordTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    static final String OLD = "old-pass-123";

    AppUser regular;
    AppUser admin;

    @BeforeEach
    void setUp() {
        regular = users.save(new AppUser("pw-regular", encoder.encode(OLD)));
        AppUser a = new AppUser("pw-admin", encoder.encode(OLD));
        a.setRole("ADMIN");
        admin = users.save(a);
    }

    private static String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    @Test
    void неверныйТекущийПарольОтклоняется() throws Exception {
        mvc.perform(post("/api/profile/password")
                        .with(user("pw-regular").roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content(body("wrong-guess", "new-pass-456")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void успешнаяСменаПрименяетНовыйХэш() throws Exception {
        mvc.perform(post("/api/profile/password")
                        .with(user("pw-regular").roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content(body(OLD, "new-pass-456")))
                .andExpect(status().isNoContent());

        AppUser reloaded = users.findByUsername("pw-regular").orElseThrow();
        assertTrue(encoder.matches("new-pass-456", reloaded.getPasswordHash()),
                "в базе должен лежать хэш нового пароля");
    }

    @Test
    void новыйСовпадающийСоСтарымОтклоняется() throws Exception {
        mvc.perform(post("/api/profile/password")
                        .with(user("pw-regular").roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content(body(OLD, OLD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void админуКороткийПарольНельзя() throws Exception {
        // для обычного пользователя 8 символов хватает, для админа минимум 12
        mvc.perform(post("/api/profile/password")
                        .with(user("pw-admin").roles("ADMIN", "USER")).with(csrf())
                        .contentType("application/json")
                        .content(body(OLD, "short-8c")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/profile/password")
                        .with(user("pw-admin").roles("ADMIN", "USER")).with(csrf())
                        .contentType("application/json")
                        .content(body(OLD, "long-enough-pass-12")))
                .andExpect(status().isNoContent());
    }
}
