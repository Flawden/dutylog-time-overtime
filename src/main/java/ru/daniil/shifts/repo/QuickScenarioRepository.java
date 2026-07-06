package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.QuickScenario;

import java.util.List;

public interface QuickScenarioRepository extends JpaRepository<QuickScenario, Long> {
    List<QuickScenario> findByOwnerOrderBySortOrderAscIdAsc(AppUser owner);
    List<QuickScenario> findByOwnerAndName(AppUser owner, String name);
}
