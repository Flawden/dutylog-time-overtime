package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    @Test
    void missingPrincipalOrNameIsRejectedAsBadRequest() {
        CurrentUserService service = new CurrentUserService(mock(UserRepository.class));
        Principal unnamed = mock(Principal.class);
        when(unnamed.getName()).thenReturn(null);

        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.requireUser(null)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> service.requireUser(unnamed)).getStatus());
    }

    @Test
    void unknownAuthenticatedPrincipalIsNotFound() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        CurrentUserService service = new CurrentUserService(users);

        ApiException error = assertThrows(ApiException.class,
                () -> service.requireUser(() -> "ghost"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void existingPrincipalResolvesToOwnerEntity() {
        UserRepository users = mock(UserRepository.class);
        AppUser owner = new AppUser("alex", "hash");
        when(users.findByUsername("alex")).thenReturn(Optional.of(owner));

        assertSame(owner, new CurrentUserService(users).requireUser(() -> "alex"));
    }
}
