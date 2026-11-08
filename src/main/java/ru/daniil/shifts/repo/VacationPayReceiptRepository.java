package ru.daniil.shifts.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayReceipt;

import java.util.List;
import java.util.Optional;

public interface VacationPayReceiptRepository
        extends JpaRepository<VacationPayReceipt, Long> {

    List<VacationPayReceipt> findByOwnerOrderByReceivedDateDescIdDesc(AppUser owner);

    Optional<VacationPayReceipt> findByIdAndOwner(Long id, AppUser owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select receipt
            from VacationPayReceipt receipt
            where receipt.id = :receiptId
              and receipt.owner = :owner
            """)
    Optional<VacationPayReceipt> findByIdAndOwnerForUpdate(
            @Param("receiptId") Long receiptId,
            @Param("owner") AppUser owner
    );

    List<VacationPayReceipt> findByOwnerAndAbsencePeriod_IdOrderByReceivedDateAscIdAsc(
            AppUser owner,
            Long absencePeriodId
    );
}
