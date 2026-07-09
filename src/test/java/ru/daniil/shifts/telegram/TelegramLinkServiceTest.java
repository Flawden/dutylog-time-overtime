package ru.daniil.shifts.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLinkCode;
import ru.daniil.shifts.repo.TelegramLinkCodeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Привязка Telegram — это выдача доступа к данным аккаунта внешнему каналу,
 * поэтому свойства кода привязки проверяются тестами, а не верой:
 * одноразовость, срок жизни, невозможность увести чужой чат.
 */
@SpringBootTest
@Transactional
class TelegramLinkServiceTest {

    @Autowired TelegramLinkService service;
    @Autowired UserRepository users;
    @Autowired TelegramLinkCodeRepository codes;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("tg-test-user", "{noop}x"));
    }

    private String freshCode() {
        return service.createCode(user).code();
    }

    @Test
    void кодПривязываетЧатКВладельцу() {
        String code = freshCode();

        service.linkByCode(code, 111L, 222L, "daniil_tg", "Даниил", null);

        assertTrue(service.findActiveUserByChatId(111L).isPresent(), "чат должен быть привязан");
        assertEquals(user.getId(), service.findActiveUserByChatId(111L).get().getId());
    }

    @Test
    void кодОдноразовый() {
        String code = freshCode();
        service.linkByCode(code, 111L, 222L, null, null, null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.linkByCode(code, 333L, 444L, null, null, null));
        assertTrue(ex.getMessage().contains("не найден или уже использован"), ex.getMessage());
    }

    @Test
    void протухшийКодОтклоняется() {
        // Код с истёкшим сроком кладём напрямую в репозиторий —
        // машину времени в тестах заменяет прямая запись прошлого.
        TelegramLinkCode expired = codes.save(
                new TelegramLinkCode(user, "DEADBEEF", Instant.now().minusSeconds(60)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.linkByCode(expired.getCode(), 111L, 222L, null, null, null));
        assertTrue(ex.getMessage().contains("истёк"), ex.getMessage());
    }

    @Test
    void несуществующийКодОтклоняется() {
        assertThrows(ApiException.class,
                () -> service.linkByCode("NO-SUCH-CODE", 111L, 222L, null, null, null));
    }

    @Test
    void чатПривязанныйКДругомуАккаунтуНеУводится() {
        // Первый пользователь привязал чат 111
        service.linkByCode(freshCode(), 111L, 222L, null, null, null);

        // Второй пользователь пытается привязать ТОТ ЖЕ чат своим кодом
        AppUser other = users.save(new AppUser("tg-other-user", "{noop}x"));
        String otherCode = service.createCode(other).code();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.linkByCode(otherCode, 111L, 999L, null, null, null));
        assertTrue(ex.getMessage().contains("уже привязан"), ex.getMessage());
    }

    @Test
    void новыйКодГаситПредыдущийНеиспользованный() {
        String first = freshCode();
        String second = freshCode(); // createCode удаляет неиспользованные коды владельца

        assertThrows(ApiException.class,
                () -> service.linkByCode(first, 111L, 222L, null, null, null),
                "старый код должен быть недействителен после выпуска нового");
        service.linkByCode(second, 111L, 222L, null, null, null); // а новый — работает
        assertTrue(service.findActiveUserByChatId(111L).isPresent());
    }
}
