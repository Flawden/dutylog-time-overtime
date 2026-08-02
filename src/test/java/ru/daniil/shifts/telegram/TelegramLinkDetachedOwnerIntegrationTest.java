package ru.daniil.shifts.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLink;
import ru.daniil.shifts.repo.TelegramLinkRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the real Telegram polling boundary: the service transaction has
 * already ended before the command handler reads the linked account timezone.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:telegram_link_detached;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TelegramLinkDetachedOwnerIntegrationTest {

    @Autowired TelegramLinkService service;
    @Autowired TelegramLinkRepository links;
    @Autowired UserRepository users;

    @Test
    void linkedOwnerRemainsReadableAfterLookupTransactionEnds() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long chatId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

        AppUser owner = new AppUser("tg-detached-" + suffix, "{noop}x");
        owner.setWorkTimezone("Asia/Yekaterinburg");
        owner = users.saveAndFlush(owner);
        links.saveAndFlush(new TelegramLink(owner, chatId));

        AppUser detachedOwner = service.findActiveUserByChatId(chatId).orElseThrow();

        String timezone = assertDoesNotThrow(detachedOwner::getWorkTimezone);
        assertEquals("Asia/Yekaterinburg", timezone);
        assertTrue(detachedOwner.getId() != null);
    }
}
