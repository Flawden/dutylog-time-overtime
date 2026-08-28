package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusP15NatureFact;

import java.util.List;

public interface PayrollSnapshotBonusP15NatureFactRepository
        extends JpaRepository<PayrollSnapshotBonusP15NatureFact, Long> {

    List<PayrollSnapshotBonusP15NatureFact> findBySnapshotOrderByFactIndexAsc(PayrollSnapshot snapshot);
}
