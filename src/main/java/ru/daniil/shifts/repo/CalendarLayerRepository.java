package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CalendarLayer;
import ru.daniil.shifts.model.ScheduleTemplate;

import java.util.List;
import java.util.Optional;

public interface CalendarLayerRepository extends JpaRepository<CalendarLayer, Long> {
    List<CalendarLayer> findByOwnerOrderBySortOrderAscIdAsc(AppUser owner);
    Optional<CalendarLayer> findByOwnerAndName(AppUser owner, String name);
    boolean existsByTemplate(ScheduleTemplate template);
}
