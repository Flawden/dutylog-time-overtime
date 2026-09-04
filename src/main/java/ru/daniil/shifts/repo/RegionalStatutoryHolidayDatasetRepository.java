package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.RegionalStatutoryHolidayDataset;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegionalStatutoryHolidayDatasetRepository extends JpaRepository<RegionalStatutoryHolidayDataset, Long> {
    Optional<RegionalStatutoryHolidayDataset> findByFingerprint(String fingerprint);

    @Query("""
            select d from RegionalStatutoryHolidayDataset d
            where d.jurisdictionCode = :jurisdictionCode
              and d.regionCode = :regionCode
              and d.coverageFrom <= :date
              and d.coverageTo >= :date
            order by d.id asc
            """)
    List<RegionalStatutoryHolidayDataset> findCovering(
            @Param("jurisdictionCode") String jurisdictionCode,
            @Param("regionCode") String regionCode,
            @Param("date") LocalDate date
    );
}
