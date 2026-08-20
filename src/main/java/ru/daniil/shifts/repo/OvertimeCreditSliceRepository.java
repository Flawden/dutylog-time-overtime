package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeCreditSlice;

import java.util.List;

public interface OvertimeCreditSliceRepository
        extends JpaRepository<OvertimeCreditSlice, Long> {

    List<OvertimeCreditSlice>
    findByCreditOrderByOffsetStartMinutesAscIdAsc(
            OvertimeCredit credit
    );

    List<OvertimeCreditSlice>
    findBySourceActualWorkIntervalOrderBySourceDateAscOffsetStartMinutesAscIdAsc(
            ActualWorkInterval sourceActualWorkInterval
    );

    void deleteByCredit(OvertimeCredit credit);

    @Query("""
            select count(slice)
            from OvertimeCreditSlice slice
            where slice.credit.id = :creditId
            """)
    long countByCreditId(
            @Param("creditId") Long creditId
    );

    @Query("""
            select count(slice)
            from OvertimeCreditSlice slice
            where slice.sourceActualWorkInterval.id = :sourceId
            """)
    long countBySourceActualWorkIntervalId(
            @Param("sourceId") Long sourceId
    );
}
