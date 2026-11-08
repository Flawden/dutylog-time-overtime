package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.daniil.shifts.model.VacationPayReceiptAdjustment;

import java.util.Collection;
import java.util.List;

public interface VacationPayReceiptAdjustmentRepository
        extends JpaRepository<VacationPayReceiptAdjustment, Long> {

    boolean existsByReceipt_Id(Long receiptId);

    boolean existsByReceipt_IdAndAdjustmentType(Long receiptId, String adjustmentType);

    @Query("""
            select coalesce(sum(adjustment.amountMinor), 0L)
            from VacationPayReceiptAdjustment adjustment
            where adjustment.receipt.id = :receiptId
              and adjustment.adjustmentType = 'REFUND'
            """)
    Long sumRefundsByReceiptId(@Param("receiptId") Long receiptId);

    List<VacationPayReceiptAdjustment>
    findByReceipt_IdOrderByCreatedAtAscIdAsc(Long receiptId);

    List<VacationPayReceiptAdjustment>
    findByReceipt_IdInOrderByCreatedAtAscIdAsc(Collection<Long> receiptIds);
}
