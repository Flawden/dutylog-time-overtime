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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регрессионный замок на защиту админки.
 *
 * Защита двухслойная: matcher `/api/admin/**` → hasRole("ADMIN") в SecurityConfig
 * плюс ручной requireAdmin() в SystemController. Эти тесты гарантируют, что
 * ни один слой не отвалится молча: обычный пользователь получает 403 на КАЖДЫЙ
 * админ-эндпоинт, аноним — 401, админ — работает.
 *
 * Добавил новый эндпоинт в SystemController — добавь его сюда. Без исключений.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAccessTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    AppUser admin;
    AppUser regular;

    @BeforeEach
    void setUp() {
        admin = new AppUser("test-admin", "{noop}x");
        admin.setRole("ADMIN");
        admin = users.save(admin);

        regular = users.save(new AppUser("test-regular", "{noop}x"));
    }

    /* ── Аноним: 401 (entry point для /api/**) ── */

    @Test
    void анонимПолучает401НаАдминке() throws Exception {
        mvc.perform(get("/api/admin/status"))
                .andExpect(status().isUnauthorized());
    }

    /* ── Обычный пользователь: 403 на каждый эндпоинт ── */

    @Test
    void обычныйПользовательНеВидитСтатус() throws Exception {
        mvc.perform(get("/api/admin/status")
                        .with(user("test-regular").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void обычныйПользовательНеВидитСписокПользователей() throws Exception {
        mvc.perform(get("/api/admin/users")
                        .with(user("test-regular").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void обычныйПользовательНеМеняетРоли() throws Exception {
        mvc.perform(patch("/api/admin/users/" + admin.getId() + "/role")
                        .with(user("test-regular").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void обычныйПользовательНеСбрасываетЧужиеПароли() throws Exception {
        mvc.perform(post("/api/admin/users/" + admin.getId() + "/password")
                        .with(user("test-regular").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"newPassword\":\"hacked123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void обычныйПользовательНеВидитИНеМеняетНастройкиРегистрации() throws Exception {
        mvc.perform(get("/api/admin/settings/registration")
                        .with(user("test-regular").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/settings/registration")
                        .with(user("test-regular").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    /* ── Админ: доступ работает ── */

    @Test
    void админВидитСтатусПользователейИНастройки() throws Exception {
        mvc.perform(get("/api/admin/status")
                        .with(user("test-admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/users")
                        .with(user("test-admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/settings/registration")
                        .with(user("test-admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk());
    }
}
