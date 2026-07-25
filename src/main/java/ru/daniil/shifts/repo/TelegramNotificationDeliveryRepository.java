package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.TelegramNotificationDelivery;

import java.time.Instant;
import java.time.LocalDateTime;

public interface TelegramNotificationDeliveryRepository extends JpaRepository<TelegramNotificationDelivery, Long> {
    boolean existsByOwnerAndReminderIdAndRemindAt(AppUser owner, String reminderId, LocalDateTime remindAt);
    boolean existsByOwnerAndReminderIdAndRemindAtInstant(AppUser owner, String reminderId, Instant remindAtInstant);
    boolean existsByOwnerAndReminderIdAndRemindAtAndRemindAtInstantIsNull(
            AppUser owner, String reminderId, LocalDateTime remindAt);
}
