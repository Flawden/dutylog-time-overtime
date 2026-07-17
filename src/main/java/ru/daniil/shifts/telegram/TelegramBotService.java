package ru.daniil.shifts.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.exception.ApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final TelegramLinkService linkService;
    private final TelegramCommandService commandService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${dutylog.telegram.polling-enabled:false}")
    private boolean pollingEnabled;

    private long updateOffset = 0L;
    private boolean pollingNow = false;

    @Autowired
    public TelegramBotService(TelegramLinkService linkService,
                              TelegramCommandService commandService,
                              ObjectMapper objectMapper) {
        this(linkService, commandService, objectMapper, new RestTemplate());
    }

    TelegramBotService(TelegramLinkService linkService,
                       TelegramCommandService commandService,
                       ObjectMapper objectMapper,
                       RestTemplate restTemplate) {
        this.linkService = linkService;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${dutylog.telegram.polling-delay-ms:3000}", initialDelay = 5000)
    public void poll() {
        if (!pollingEnabled || !linkService.isConfigured() || pollingNow) return;
        pollingNow = true;
        try {
            String token = linkService.token();
            String url = apiUrl(token, "getUpdates")
                    + "?timeout=0"
                    + "&offset=" + updateOffset
                    + "&allowed_updates=" + URLEncoder.encode("[\"message\"]", StandardCharsets.UTF_8);
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root == null || !root.path("ok").asBoolean(false)) return;
            for (JsonNode update : root.path("result")) {
                long updateId = update.path("update_id").asLong();
                try {
                    handleUpdate(update);
                } catch (Exception e) {
                    log.warn("Telegram update handling failed: {}", e.getMessage());
                } finally {
                    updateOffset = Math.max(updateOffset, updateId + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Telegram polling failed: {}", safeErrorMessage(e));
        } finally {
            pollingNow = false;
        }
    }

    private void handleUpdate(JsonNode update) {
        JsonNode message = update.path("message");
        JsonNode chatIdNode = message.path("chat").path("id");
        if (message.isMissingNode() || message.path("text").isMissingNode()
                || chatIdNode.isMissingNode() || chatIdNode.isNull()) return;

        Long chatId = chatIdNode.asLong();
        JsonNode from = message.path("from");
        Long telegramUserId = from.path("id").isMissingNode() ? null : from.path("id").asLong();
        String username = textOrNull(from, "username");
        String firstName = textOrNull(from, "first_name");
        String lastName = textOrNull(from, "last_name");
        String text = message.path("text").asText("").trim();

        if (isStartWithCode(text) || looksLikeCode(text)) {
            try {
                String code = codeFromText(text);
                String displayName = linkService.linkByCode(code, chatId, telegramUserId, username, firstName, lastName);
                sendMessage(chatId, "Готово. Telegram подключён к DutyLog для аккаунта " + displayName + ".\n\n" + commandService.help());
            } catch (ApiException e) {
                sendMessage(chatId, e.getMessage());
            }
            return;
        }

        AppUser user = linkService.findActiveUserByChatId(chatId).orElse(null);
        if (user == null) {
            sendMessage(chatId, "Сначала привяжи Telegram к DutyLog. Открой приложение → ⚙ → Telegram → создай код и отправь сюда /start DL-123456.");
            return;
        }

        try {
            String answer = commandService.handle(user, text);
            sendMessage(chatId, answer);
        } catch (ApiException e) {
            sendMessage(chatId, e.getMessage());
        }
    }

    public boolean sendMessage(Long chatId, String text) {
        if (!linkService.isConfigured() || chatId == null || text == null || text.isBlank()) return false;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text.length() > 3900 ? text.substring(0, 3900) + "…" : text);
            body.put("disable_web_page_preview", true);
            JsonNode root = restTemplate.postForObject(apiUrl(linkService.token(), "sendMessage"), body, JsonNode.class);
            return root != null && root.path("ok").asBoolean(false);
        } catch (Exception e) {
            log.warn("Telegram send failed: {}", safeErrorMessage(e));
            return false;
        }
    }

    String safeErrorMessage(Exception error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) return "request failed";
        String token = linkService.token();
        return token == null || token.isBlank() ? message : message.replace(token, "***");
    }

    private String apiUrl(String token, String method) {
        return "https://api.telegram.org/bot" + token + "/" + method;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private boolean isStartWithCode(String text) {
        return text != null && text.trim().toUpperCase().matches("^/START(@\\w+)?\\s+DL-\\d{6}.*$");
    }

    private boolean looksLikeCode(String text) {
        return text != null && text.trim().toUpperCase().matches("^DL-\\d{6}$");
    }

    private String codeFromText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.toUpperCase().startsWith("/START")) {
            String[] parts = trimmed.split("\\s+", 2);
            return parts.length > 1 ? parts[1].trim().split("\\s+")[0] : "";
        }
        return trimmed;
    }
}
