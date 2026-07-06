package ru.daniil.shifts.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.telegram.TelegramLinkService;
import ru.daniil.shifts.telegram.TelegramLinkService.TelegramCodeDto;
import ru.daniil.shifts.telegram.TelegramLinkService.TelegramStatusDto;
import ru.daniil.shifts.telegram.TelegramLinkService.TelegramSettingsRequest;

import java.security.Principal;

/**
 * Browser-safe API для привязки Telegram к текущему web-аккаунту.
 * Сам bot polling живёт в TelegramBotService и работает только если задан токен.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramController {
    private final CurrentUserService currentUserService;
    private final TelegramLinkService telegramLinkService;

    public TelegramController(CurrentUserService currentUserService,
                              TelegramLinkService telegramLinkService) {
        this.currentUserService = currentUserService;
        this.telegramLinkService = telegramLinkService;
    }

    @GetMapping("/status")
    public TelegramStatusDto status(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return telegramLinkService.status(current);
    }

    @PostMapping("/link-code")
    public TelegramCodeDto createLinkCode(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return telegramLinkService.createCode(current);
    }

    @PatchMapping("/settings")
    public TelegramStatusDto updateSettings(Principal principal, @RequestBody TelegramSettingsRequest request) {
        AppUser current = currentUserService.requireUser(principal);
        return telegramLinkService.updateSettings(current, request);
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        telegramLinkService.unlink(current);
        return ResponseEntity.noContent().build();
    }
}
