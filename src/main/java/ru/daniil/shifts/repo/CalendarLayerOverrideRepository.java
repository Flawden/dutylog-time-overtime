package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.CalendarLayer;
import ru.daniil.shifts.model.CalendarLayerOverride;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CalendarLayerOverrideRepository extends JpaRepository<CalendarLayerOverride, Long> {
    Optional<CalendarLayerOverride> findByLayerAndSourceDate(CalendarLayer layer, LocalDate sourceDate);

    List<CalendarLayerOverride> findByLayerAndSourceDateBetweenOrderBySourceDateAsc(
            CalendarLayer layer, LocalDate from, LocalDate to);
}
