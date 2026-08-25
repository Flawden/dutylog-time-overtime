package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningLine;

import java.util.List;

public interface PayrollSnapshotEarningLineRepository
        extends JpaRepository<PayrollSnapshotEarningLine, Long> {

    List<PayrollSnapshotEarningLine>
    findBySnapshotOrderByLineIndexAsc(
            PayrollSnapshot snapshot
    );
}
