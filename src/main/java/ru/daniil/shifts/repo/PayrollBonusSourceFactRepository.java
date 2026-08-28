package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusSourceFact;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollBonusSourceFactRepository
        extends JpaRepository<PayrollBonusSourceFact, Long> {

    List<PayrollBonusSourceFact>
    findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            long componentId,
            PayrollEarningKind earningKind
    );

    List<PayrollBonusSourceFact>
    findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
            AppUser owner,
            LocalDate from,
            LocalDate to
    );

    Optional<PayrollBonusSourceFact> findByOwnerAndId(
            AppUser owner,
            Long id
    );
}
