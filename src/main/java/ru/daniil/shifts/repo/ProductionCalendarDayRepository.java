package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ProductionCalendarDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductionCalendarDayRepository extends JpaRepository<ProductionCalendarDay, Long> {
    List<ProductionCalendarDay> findByOwnerAndDateBetweenOrderByDateAscLayerAsc(
            AppUser owner, LocalDate from, LocalDate to);
    Optional<ProductionCalendarDay> findByOwnerAndDateAndLayer(AppUser owner, LocalDate date, String layer);
}
