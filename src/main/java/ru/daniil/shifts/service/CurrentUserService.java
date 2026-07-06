package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public AppUser requireUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw ApiException.badRequest("Пользователь не авторизован");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }
}
