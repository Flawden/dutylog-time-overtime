package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotComponentLine;

import java.util.List;

public interface PayrollSnapshotComponentLineRepository
        extends JpaRepository<PayrollSnapshotComponentLine, Long> {

    List<PayrollSnapshotComponentLine>
    findBySnapshotOrderByLineIndexAsc(
            PayrollSnapshot snapshot
    );
}
