package ru.daniil.shifts.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramLink;
import ru.daniil.shifts.model.TelegramLinkCode;
import ru.daniil.shifts.repo.TelegramLinkCodeRepository;
import ru.daniil.shifts.repo.TelegramLinkRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class TelegramLinkService {
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelegramLinkRepository links;
    private final TelegramLinkCodeRepository codes;

    @Value("${dutylog.telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${dutylog.telegram.polling-enabled:false}")
    private boolean pollingEnabled;

    @Value("${dutylog.telegram.bot-token:}")
    private String botToken;

    @Value("${dutylog.telegram.bot-username:}")
    private String botUsername;

    public TelegramLinkService(TelegramLinkRepository links, TelegramLinkCodeRepository codes) {
        this.links = links;
        this.codes = codes;
    }

    public record TelegramStatusDto(
            boolean configured,
            boolean pollingEnabled,
            boolean linked,
            boolean enabled,
            String botUsername,
            String chatId,
            String username,
            String linkedAt,
            String pendingCode,
            String pendingCodeExpiresAt
    ) {}

    public record TelegramCodeDto(
            String code,
            String expiresAt,
            String startCommand,
            String deepLink
    ) {}

    @Transactional(readOnly = true)
    public TelegramStatusDto status(AppUser user) {
        TelegramLink link = links.findByOwner(user).orElse(null);
        TelegramLinkCode pending = codes.findByOwnerAndUsedAtIsNullOrderByCreatedAtDesc(user).stream()
                .filter(c -> !c.isExpired())
                .findFirst()
                .orElse(null);
        return new TelegramStatusDto(
                isConfigured(),
                pollingEnabled,
                link != null,
                link == null || link.isEnabled(),
                cleanBotUsername(),
                link != null ? String.valueOf(link.getTelegramChatId()) : null,
                link != null ? link.getUsername() : null,
                link != null && link.getLinkedAt() != null ? link.getLinkedAt().toString() : null,
                pending != null ? pending.getCode() : null,
                pending != null ? pending.getExpiresAt().toString() : null
        );
    }

    @Transactional
    public TelegramCodeDto createCode(AppUser user) {
        codes.deleteByExpiresAtBeforeAndUsedAtIsNull(Instant.now());
        codes.deleteByOwnerAndUsedAtIsNull(user);
        String code = newCode();
        Instant expiresAt = Instant.now().plus(CODE_TTL);
        TelegramLinkCode saved = codes.save(new TelegramLinkCode(user, code, expiresAt));
        String command = "/start " + saved.getCode();
        String username = cleanBotUsername();
        String deepLink = username != null ? "https://t.me/" + username + "?start=" + saved.getCode() : null;
        return new TelegramCodeDto(saved.getCode(), saved.getExpiresAt().toString(), command, deepLink);
    }

    @Transactional
    public String linkByCode(String rawCode,
                             Long telegramChatId,
                             Long telegramUserId,
                             String username,
                             String firstName,
                             String lastName) {
        String code = normalizeCode(rawCode);
        TelegramLinkCode linkCode = codes.findByCodeAndUsedAtIsNull(code)
                .orElseThrow(() -> ApiException.badRequest("Код не найден или уже использован"));
        if (linkCode.isExpired()) {
            throw ApiException.badRequest("Код истёк. Создай новый код в DutyLog → ⚙ → Telegram");
        }

        AppUser owner = linkCode.getOwner();
        Optional<TelegramLink> existingChat = links.findByTelegramChatId(telegramChatId);
        if (existingChat.isPresent() && !existingChat.get().getOwner().getId().equals(owner.getId())) {
            throw ApiException.conflict("Этот Telegram уже привязан к другому аккаунту DutyLog");
        }

        TelegramLink link = links.findByOwner(owner).orElseGet(() -> new TelegramLink(owner, telegramChatId));
        link.setTelegramChatId(telegramChatId);
        link.setTelegramUserId(telegramUserId);
        link.setUsername(clean(username, 64));
        link.setFirstName(clean(firstName, 80));
        link.setLastName(clean(lastName, 80));
        link.setEnabled(true);
        links.save(link);

        linkCode.setUsedAt(Instant.now());
        codes.save(linkCode);
        return displayName(owner);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramLink> findActiveByChatId(Long telegramChatId) {
        return links.findByTelegramChatId(telegramChatId).filter(TelegramLink::isEnabled);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findActiveUserByChatId(Long telegramChatId) {
        return links.findByTelegramChatId(telegramChatId)
                .filter(TelegramLink::isEnabled)
                .map(TelegramLink::getOwner);
    }

    @Transactional
    public void unlink(AppUser user) {
        links.findByOwner(user).ifPresent(links::delete);
        codes.deleteByOwnerAndUsedAtIsNull(user);
    }

    public boolean isConfigured() {
        return telegramEnabled && botToken != null && !botToken.isBlank();
    }

    public String token() { return botToken == null ? "" : botToken.trim(); }

    private String cleanBotUsername() {
        String v = clean(botUsername, 80);
        if (v == null || v.isBlank()) return null;
        return v.startsWith("@") ? v.substring(1) : v;
    }

    private String newCode() {
        for (int i = 0; i < 25; i++) {
            String candidate = "DL-" + (100000 + RANDOM.nextInt(900000));
            if (!codes.existsByCode(candidate)) return candidate;
        }
        throw ApiException.conflict("Не удалось создать код привязки. Попробуй ещё раз");
    }

    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (code.startsWith("/START")) {
            String[] parts = code.split("\\s+", 2);
            code = parts.length > 1 ? parts[1].trim() : "";
        }
        if (!code.matches("DL-\\d{6}")) {
            throw ApiException.badRequest("Код должен выглядеть как DL-123456");
        }
        return code;
    }

    private String clean(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    private String displayName(AppUser user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getUsername();
    }
}
