package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ImportantDay;

import java.util.List;

public interface ImportantDayRepository extends JpaRepository<ImportantDay, Long> {
    List<ImportantDay> findByOwnerOrderByDateAscIdAsc(AppUser owner);
}
