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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final List<Map<String, String>> COMMAND_MENU = List.of(
            command("today", "Сводка на сегодня"),
            command("tomorrow", "Сводка на завтра"),
            command("week", "Ближайшие 7 дней"),
            command("tasks", "Открытые задачи"),
            command("balance", "Баланс переработок"),
            command("task", "Добавить задачу"),
            command("done", "Закрыть задачу по id"),
            command("ppr", "Начислить переработку"),
            command("timeoff", "Списать часы"),
            command("help", "Все команды и примеры")
    );
    private static final Map<String, Object> QUICK_ACTION_KEYBOARD = quickActionKeyboard();

    private final TelegramLinkService linkService;
    private final TelegramCommandService commandService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${dutylog.telegram.polling-enabled:false}")
    private boolean pollingEnabled;

    @Value("${dutylog.telegram.command-menu-enabled:true}")
    private boolean commandMenuEnabled;

    private long updateOffset = 0L;
    private boolean pollingNow = false;
    private boolean commandMenuRefreshing = false;

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


    @Scheduled(
            fixedDelayString = "${dutylog.telegram.command-menu-refresh-ms:21600000}",
            initialDelayString = "${dutylog.telegram.command-menu-initial-delay-ms:7000}"
    )
    public void refreshCommandMenu() {
        if (!commandMenuEnabled || !pollingEnabled || !linkService.isConfigured() || commandMenuRefreshing) return;
        commandMenuRefreshing = true;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("commands", COMMAND_MENU);
            JsonNode root = restTemplate.postForObject(apiUrl(linkService.token(), "setMyCommands"), body, JsonNode.class);
            if (root == null || !root.path("ok").asBoolean(false)) {
                log.warn("Telegram command menu refresh was rejected");
                return;
            }
            log.info("Telegram command menu refreshed: {} commands", COMMAND_MENU.size());
        } catch (Exception e) {
            log.warn("Telegram command menu refresh failed: {}", safeErrorMessage(e));
        } finally {
            commandMenuRefreshing = false;
        }
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
            if (answer == null || answer.isBlank()) {
                log.warn("Telegram command returned an empty answer: chatId={} command={}", chatId, commandName(text));
                sendMessage(chatId, "Команда выполнилась без ответа. Повтори ещё раз или напиши /help.");
            } else {
                sendMessage(chatId, answer);
            }
        } catch (ApiException e) {
            sendMessage(chatId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Telegram command failed: chatId={} command={} exceptionType={}",
                    chatId, commandName(text), e.getClass().getSimpleName());
            sendMessage(chatId, "Не удалось выполнить команду. Попробуй ещё раз через минуту. Если повторится — сообщи администратору.");
        }
    }

    public boolean sendMessage(Long chatId, String text) {
        if (!linkService.isConfigured() || chatId == null || text == null || text.isBlank()) return false;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text.length() > 3900 ? text.substring(0, 3900) + "…" : text);
            body.put("disable_web_page_preview", true);
            if (commandMenuEnabled && pollingEnabled) body.put("reply_markup", QUICK_ACTION_KEYBOARD);
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

    private String commandName(String text) {
        if (text == null || text.isBlank()) return "<empty>";
        String first = text.trim().split("\\s+", 2)[0];
        int at = first.indexOf('@');
        if (at >= 0) first = first.substring(0, at);
        return first.length() > 40 ? first.substring(0, 40) : first;
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

    private static Map<String, String> command(String command, String description) {
        return Map.of("command", command, "description", description);
    }

    private static Map<String, Object> quickActionKeyboard() {
        Map<String, Object> keyboard = new LinkedHashMap<>();
        keyboard.put("keyboard", List.of(
                List.of(button("Сегодня"), button("Завтра")),
                List.of(button("Задачи"), button("Баланс")),
                List.of(button("Неделя"), button("Помощь"))
        ));
        keyboard.put("resize_keyboard", true);
        keyboard.put("is_persistent", true);
        keyboard.put("input_field_placeholder", "Выбери действие или введи команду");
        return Map.copyOf(keyboard);
    }

    private static Map<String, String> button(String text) {
        return Map.of("text", text);
    }

}
