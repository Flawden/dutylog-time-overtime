package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CalendarFeedSubscription;

import java.util.Optional;

public interface CalendarFeedSubscriptionRepository extends JpaRepository<CalendarFeedSubscription, Long> {
    Optional<CalendarFeedSubscription> findByOwner(AppUser owner);
    @EntityGraph(attributePaths = "owner")
    Optional<CalendarFeedSubscription> findByTokenHash(String tokenHash);
}
