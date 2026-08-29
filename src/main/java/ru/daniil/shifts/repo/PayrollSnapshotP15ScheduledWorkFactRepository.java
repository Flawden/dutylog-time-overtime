package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotP15ScheduledWorkFact;

import java.util.List;

public interface PayrollSnapshotP15ScheduledWorkFactRepository
        extends JpaRepository<PayrollSnapshotP15ScheduledWorkFact, Long> {

    List<PayrollSnapshotP15ScheduledWorkFact>
    findBySnapshotOrderByFactIndexAsc(PayrollSnapshot snapshot);
}
