package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDataset;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDateFact;

import java.util.List;

public interface RegionalStatutoryHolidayDateFactRepository extends JpaRepository<RegionalStatutoryHolidayDateFact, Long> {
    List<RegionalStatutoryHolidayDateFact> findByDatasetOrderByHolidayDateAscIdAsc(RegionalStatutoryHolidayDataset dataset);
}
