package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.NotificationReminderDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsDto;
import ru.daniil.shifts.dto.Dtos.NotificationSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.NotificationService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public NotificationController(CurrentUserService currentUserService, NotificationService notificationService) {
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @GetMapping("/settings")
    public NotificationSettingsDto settings(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return notificationService.settings(user);
    }

    @PatchMapping("/settings")
    public NotificationSettingsDto update(@Valid @RequestBody NotificationSettingsUpdateRequest req, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return notificationService.update(user, req);
    }

    @GetMapping("/upcoming")
    public List<NotificationReminderDto> upcoming(@RequestParam("from") String from,
                                                  @RequestParam("to") String to,
                                                  @RequestParam(name = "includePast", defaultValue = "true") boolean includePast,
                                                  Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        LocalDate fromDate = notificationService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = notificationService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return notificationService.upcoming(user, fromDate, toDate, includePast);
    }

    @GetMapping("/tomorrow")
    public List<NotificationReminderDto> tomorrow(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return notificationService.tomorrow(user);
    }
}
