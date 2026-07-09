package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.service.AppSettingsService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регистрация — публичный эндпоинт, единственная дверь без логина.
 * Проверяем: административный выключатель реально закрывает дверь,
 * валидация и дубли работают, статус честно отражает состояние.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppSettingsService settings;

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void приВыключеннойРегистрацииДверьЗакрыта() throws Exception {
        settings.setRegistrationEnabled(false, "test");

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("newcomer", "secret123")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.mode").value("closed"));
    }

    @Test
    void приВключеннойРегистрацииПользовательСоздаётся() throws Exception {
        settings.setRegistrationEnabled(true, "test");

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("newcomer", "secret123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newcomer"));
    }

    @Test
    void дубльИмениОтклоняется409() throws Exception {
        settings.setRegistrationEnabled(true, "test");

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("twin", "secret123")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("twin", "another123")))
                .andExpect(status().isConflict());
    }

    @Test
    void короткийПарольИКривоеИмяОтклоняются() throws Exception {
        settings.setRegistrationEnabled(true, "test");

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("ok-name", "12345")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("ab", "secret123")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(body("bad name!", "secret123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void безCsrfТокенаРегистрацияНеПроходит() throws Exception {
        settings.setRegistrationEnabled(true, "test");

        // Без csrf() — имитация запроса без токена: SPA-защита должна сработать
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(body("no-csrf", "secret123")))
                .andExpect(status().isUnauthorized()); // аноним + CSRF-отказ → entry point /api/** → 401
    }
}
