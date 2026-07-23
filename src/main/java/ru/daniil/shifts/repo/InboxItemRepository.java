package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.InboxItem;
import ru.daniil.shifts.model.InboxItemStatus;

import java.util.List;
import java.util.Optional;

public interface InboxItemRepository extends JpaRepository<InboxItem, Long> {
    List<InboxItem> findByOwnerAndStatusOrderByCreatedAtDescIdDesc(AppUser owner, InboxItemStatus status);
    List<InboxItem> findByOwnerOrderByCreatedAtDescIdDesc(AppUser owner);
    Optional<InboxItem> findByOwnerAndClientOperationId(AppUser owner, String clientOperationId);
}
