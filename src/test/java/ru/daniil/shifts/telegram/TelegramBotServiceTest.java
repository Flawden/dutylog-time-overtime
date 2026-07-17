package ru.daniil.shifts.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit-level Telegram HTTP contract: polling, linking, command dispatch and fail-closed delivery. */
@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock TelegramLinkService linkService;
    @Mock TelegramCommandService commandService;

    ObjectMapper mapper;
    RestTemplate restTemplate;
    MockRestServiceServer server;
    TelegramBotService bot;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        bot = new TelegramBotService(linkService, commandService, mapper, restTemplate);
        ReflectionTestUtils.setField(bot, "pollingEnabled", true);
        lenient().when(linkService.token()).thenReturn("secret-token");
    }

    @Test
    void disabledUnconfiguredAndOverlappingPollingNeverCallTelegram() {
        ReflectionTestUtils.setField(bot, "pollingEnabled", false);
        bot.poll();
        verifyNoInteractions(linkService, commandService);

        ReflectionTestUtils.setField(bot, "pollingEnabled", true);
        when(linkService.isConfigured()).thenReturn(false);
        bot.poll();

        when(linkService.isConfigured()).thenReturn(true);
        ReflectionTestUtils.setField(bot, "pollingNow", true);
        bot.poll();

        server.verify();
        verify(commandService, never()).handle(any(), anyString());
    }

    @Test
    void startCodeLinksTelegramAndAdvancesOffset() throws Exception {
        when(linkService.isConfigured()).thenReturn(true);
        when(linkService.linkByCode("DL-123456", 700L, 900L, "tester", "Test", "User"))
                .thenReturn("DutyLog User");
        when(commandService.help()).thenReturn("HELP");

        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/getUpdates")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("offset=0")))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[{"update_id":41,"message":{"chat":{"id":700},"from":{"id":900,"username":"tester","first_name":"Test","last_name":"User"},"text":"/start@DutyLogBot DL-123456 extra"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DutyLog User")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HELP")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        bot.poll();

        assertEquals(42L, ReflectionTestUtils.getField(bot, "updateOffset"));
        assertEquals(false, ReflectionTestUtils.getField(bot, "pollingNow"));
        verify(linkService).linkByCode("DL-123456", 700L, 900L, "tester", "Test", "User");
        server.verify();
    }

    @Test
    void bareCodeFailureIsReturnedToChatAndOffsetStillMoves() {
        when(linkService.isConfigured()).thenReturn(true);
        when(linkService.linkByCode(eq("DL-654321"), anyLong(), isNull(), isNull(), isNull(), isNull()))
                .thenThrow(ApiException.badRequest("Код истёк"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/getUpdates")))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[{"update_id":9,"message":{"chat":{"id":77},"from":{},"text":"DL-654321"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Код истёк")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        bot.poll();

        assertEquals(10L, ReflectionTestUtils.getField(bot, "updateOffset"));
        server.verify();
    }

    @Test
    void unlinkedChatGetsLinkInstructions() {
        when(linkService.isConfigured()).thenReturn(true);
        when(linkService.findActiveUserByChatId(88L)).thenReturn(Optional.empty());

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/getUpdates")))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[{"update_id":1,"message":{"chat":{"id":88},"from":{"id":5},"text":"/today"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сначала привяжи Telegram")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        bot.poll();

        verify(commandService, never()).handle(any(), anyString());
        server.verify();
    }

    @Test
    void linkedCommandAndExpectedApiErrorAreSentBack() {
        AppUser user = new AppUser("telegram-owner", "{noop}x");
        when(linkService.isConfigured()).thenReturn(true);
        when(linkService.findActiveUserByChatId(90L)).thenReturn(Optional.of(user));
        when(commandService.handle(user, "/today")).thenReturn("Сегодня всё хорошо");

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/getUpdates")))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[
                          {"update_id":5,"message":{"chat":{"id":90},"text":" /today "}},
                          {"update_id":6,"message":{"chat":{"id":90},"text":"/done nope"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сегодня всё хорошо")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        when(commandService.handle(user, "/done nope"))
                .thenThrow(ApiException.badRequest("Укажи id задачи"));
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Укажи id задачи")))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        bot.poll();

        assertEquals(7L, ReflectionTestUtils.getField(bot, "updateOffset"));
        server.verify();
    }

    @Test
    void updatesWithoutTextOrChatAreIgnoredButAcknowledgedByOffset() {
        when(linkService.isConfigured()).thenReturn(true);
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/getUpdates")))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[
                          {"update_id":12,"message":{"chat":{"id":10},"photo":[]}},
                          {"update_id":13,"message":{"text":"/today"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        bot.poll();

        assertEquals(14L, ReflectionTestUtils.getField(bot, "updateOffset"));
        verify(linkService, never()).findActiveUserByChatId(anyLong());
        server.verify();
    }

    @Test
    void sendMessageValidatesInputTruncatesTextAndFailsClosed() throws Exception {
        when(linkService.isConfigured()).thenReturn(false);
        assertFalse(bot.sendMessage(1L, "hello"));
        assertFalse(bot.sendMessage(null, "hello"));
        assertFalse(bot.sendMessage(1L, "  "));

        when(linkService.isConfigured()).thenReturn(true);
        String longText = "x".repeat(4100);
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andExpect(request -> {
                    JsonNode body = mapper.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(1L, body.path("chat_id").asLong());
                    assertEquals(3901, body.path("text").asText().length());
                    assertTrue(body.path("text").asText().endsWith("…"));
                    assertTrue(body.path("disable_web_page_preview").asBoolean());
                })
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.telegram.org/botsecret-token/sendMessage"))
                .andRespond(withSuccess());

        assertFalse(bot.sendMessage(1L, longText), "Telegram ok=false must never count as delivery");
        assertFalse(bot.sendMessage(1L, "empty response"), "empty Telegram response must fail closed");
        server.verify();
    }

    @Test
    void tokenIsRedactedFromLoggedErrorMessage() {
        RuntimeException failure = new RuntimeException("POST https://api.telegram.org/botsecret-token/sendMessage failed");
        String safe = bot.safeErrorMessage(failure);

        assertFalse(safe.contains("secret-token"));
        assertTrue(safe.contains("bot***/sendMessage"));
        assertEquals("request failed", bot.safeErrorMessage(new RuntimeException()));
        assertEquals("request failed", bot.safeErrorMessage(null));
    }
}
