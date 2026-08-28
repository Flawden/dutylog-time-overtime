package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;

import java.util.List;

public interface PayrollSnapshotBonusAverageEarningsFactRepository
        extends JpaRepository<PayrollSnapshotBonusAverageEarningsFact, Long> {

    List<PayrollSnapshotBonusAverageEarningsFact>
    findBySnapshotOrderByFactIndexAsc(PayrollSnapshot snapshot);
}
