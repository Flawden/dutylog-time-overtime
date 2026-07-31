package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.CalendarSubscriptionDto;
import ru.daniil.shifts.dto.Dtos.CalendarSyncStatusDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CalendarFeedSubscription;
import ru.daniil.shifts.repo.CalendarFeedSubscriptionRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CalendarSubscriptionServiceTest {

    @Test
    void inactiveStatusDoesNotInventARecoverableUrl() {
        CalendarFeedSubscriptionRepository repo = mock(CalendarFeedSubscriptionRepository.class);
        when(repo.findByOwner(any())).thenReturn(Optional.empty());
        CalendarSubscriptionService service = service(repo, mock(ModuleService.class));

        CalendarSyncStatusDto status = service.status(new AppUser("owner", "hash"));

        assertFalse(status.active());
        assertNull(status.tokenHint());
        assertEquals(30, status.feedPastDays());
        assertEquals(335, status.feedFutureDays());
        assertEquals(CalendarSubscriptionService.ENTITIES, status.entities());
    }

    @Test
    void issuePersistsOnlySha256AndReturnsRawTokenOnce() {
        CalendarFeedSubscriptionRepository repo = mock(CalendarFeedSubscriptionRepository.class);
        when(repo.findByOwner(any())).thenReturn(Optional.empty());
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CalendarSubscriptionService service = service(repo, mock(ModuleService.class));
        AppUser owner = new AppUser("owner", "hash");

        CalendarSubscriptionService.IssueResult issued = service.issue(owner);

        assertTrue(issued.rawToken().matches("[A-Za-z0-9_-]{43}"));
        ArgumentCaptor<CalendarFeedSubscription> captor = ArgumentCaptor.forClass(CalendarFeedSubscription.class);
        verify(repo).saveAndFlush(captor.capture());
        CalendarFeedSubscription stored = captor.getValue();
        assertEquals(CalendarSubscriptionService.hash(issued.rawToken()), stored.getTokenHash());
        assertNotEquals(issued.rawToken(), stored.getTokenHash());
        assertFalse(stored.getTokenHint().contains(issued.rawToken()));

        CalendarSubscriptionDto dto = service.toIssuedDto(issued,
                "https://example.test/calendar-feed.ics?token=" + issued.rawToken());
        assertTrue(dto.subscriptionUrl().contains(issued.rawToken()));
        assertEquals(stored.getTokenHint(), dto.tokenHint());
    }

    @Test
    void existingSubscriptionIsRotatedInsteadOfDuplicated() {
        CalendarFeedSubscriptionRepository repo = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedSubscription existing = mock(CalendarFeedSubscription.class);
        when(existing.getId()).thenReturn(7L);
        when(existing.getTokenHint()).thenReturn("before…hint");
        when(repo.findByOwner(any())).thenReturn(Optional.of(existing));
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(existing)).thenReturn(existing);
        CalendarSubscriptionService service = service(repo, mock(ModuleService.class));

        service.issue(new AppUser("owner", "hash"));

        verify(existing).rotate(matches("[0-9a-f]{64}"), matches(".{11}"));
        verify(repo).saveAndFlush(existing);
    }

    @Test
    void resolveRequiresAValidTokenAndEnabledModule() {
        CalendarFeedSubscriptionRepository repo = mock(CalendarFeedSubscriptionRepository.class);
        ModuleService modules = mock(ModuleService.class);
        AppUser owner = new AppUser("owner", "hash");
        String raw = "A".repeat(43);
        CalendarFeedSubscription subscription = new CalendarFeedSubscription(
                owner, CalendarSubscriptionService.hash(raw), "AAAAAA…AAAA");
        when(repo.findByTokenHash(CalendarSubscriptionService.hash(raw))).thenReturn(Optional.of(subscription));
        when(modules.isEnabled(owner, ModuleService.CALENDAR_SYNC)).thenReturn(true, false);
        CalendarSubscriptionService service = service(repo, modules);

        assertSame(owner, service.resolveOwner(raw));
        assertEquals(404, assertThrows(ApiException.class, () -> service.resolveOwner(raw)).getStatus().value());
        assertEquals(404, assertThrows(ApiException.class, () -> service.resolveOwner("bad token")).getStatus().value());
    }

    @Test
    void revokeDeletesOnlyTheOwnersSubscriptionAndFeedWindowIsBounded() {
        CalendarFeedSubscriptionRepository repo = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedSubscription subscription = mock(CalendarFeedSubscription.class);
        when(subscription.getTokenHint()).thenReturn("abcdef…wxyz");
        when(repo.findByOwner(any())).thenReturn(Optional.of(subscription));
        CalendarSubscriptionService service = service(repo, mock(ModuleService.class));

        service.revoke(new AppUser("owner", "hash"));
        CalendarSubscriptionService.DateRange range = service.feedRange(LocalDate.of(2026, 7, 31));

        verify(repo).delete(subscription);
        verify(repo).flush();
        assertEquals(LocalDate.of(2026, 7, 1), range.from());
        assertEquals(LocalDate.of(2027, 7, 1), range.to());
        assertEquals(365, java.time.temporal.ChronoUnit.DAYS.between(range.from(), range.to()));
    }

    private static CalendarSubscriptionService service(CalendarFeedSubscriptionRepository repo, ModuleService modules) {
        return new CalendarSubscriptionService(repo, modules, mock(SecurityEventLogger.class), 30, 335);
    }
}
