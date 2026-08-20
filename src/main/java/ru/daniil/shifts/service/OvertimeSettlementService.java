package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeSettlement;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;

/**
 * Business owner for explicit cash-settlement decisions.
 *
 * Pricing and Payroll are intentionally absent from this service. Creating a
 * settlement only consumes canonical Time Bank minutes through the existing
 * FIFO engine.
 */
@Service
public class OvertimeSettlementService {

    private final OvertimeSettlementRepository settlements;
    private final OvertimeService overtime;

    public OvertimeSettlementService(
            OvertimeSettlementRepository settlements,
            OvertimeService overtime
    ) {
        this.settlements = settlements;
        this.overtime = overtime;
    }

    @Transactional(readOnly = true)
    public List<OvertimeSettlement> list(
            AppUser user
    ) {
        return settlements
                .findByOwnerOrderBySettlementDateAscIdAsc(
                        user
                );
    }

    @Transactional
    public OvertimeSettlement create(
            AppUser user,
            LocalDate settlementDate,
            int requestedMinutes,
            String reason
    ) {
        validate(
                settlementDate,
                requestedMinutes
        );

        String cleanReason =
                cleanReason(reason);

        OvertimeSettlement saved =
                settlements.saveAndFlush(
                        new OvertimeSettlement(
                                user,
                                settlementDate,
                                requestedMinutes,
                                cleanReason
                        )
                );

        /*
         * If capacity/period/FIFO validation fails, the outer transaction rolls
         * back the freshly created settlement as well.
         */
        overtime.upsertLinkedSettlementUsage(
                user,
                saved.getId(),
                settlementDate,
                requestedMinutes,
                cleanReason
        );

        return saved;
    }

    @Transactional
    public OvertimeSettlement update(
            AppUser user,
            Long id,
            LocalDate settlementDate,
            int requestedMinutes,
            String reason
    ) {
        OvertimeSettlement settlement =
                requireOwned(
                        user,
                        id
                );

        validate(
                settlementDate,
                requestedMinutes
        );

        String cleanReason =
                cleanReason(reason);

        /*
         * Bank projection validates both old and new periods before the
         * business row is changed.
         */
        overtime.upsertLinkedSettlementUsage(
                user,
                settlement.getId(),
                settlementDate,
                requestedMinutes,
                cleanReason
        );

        settlement.update(
                settlementDate,
                requestedMinutes,
                cleanReason
        );

        return settlements.saveAndFlush(
                settlement
        );
    }

    @Transactional
    public void delete(
            AppUser user,
            Long id
    ) {
        OvertimeSettlement settlement =
                requireOwned(
                        user,
                        id
                );

        /*
         * Allocations -> usage -> settlement.
         * V4 allocation FKs do not cascade from usage.
         */
        overtime.deleteLinkedSettlementUsage(
                user,
                settlement.getId()
        );

        settlements.delete(
                settlement
        );
        settlements.flush();
    }

    private OvertimeSettlement requireOwned(
            AppUser user,
            Long id
    ) {
        if (id == null) {
            throw ApiException.notFound(
                    "Settlement переработки не найден"
            );
        }

        return settlements
                .findByOwnerAndId(
                        user,
                        id
                )
                .orElseThrow(() ->
                        ApiException.notFound(
                                "Settlement переработки не найден"
                        )
                );
    }

    private void validate(
            LocalDate settlementDate,
            int requestedMinutes
    ) {
        if (settlementDate == null) {
            throw ApiException.badRequest(
                    "Дата settlement обязательна"
            );
        }

        if (requestedMinutes <= 0
                || requestedMinutes > 6000) {
            throw ApiException.badRequest(
                    "Settlement должен содержать от 1 до 6000 минут"
            );
        }
    }

    private String cleanReason(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return null;
        }

        String clean =
                value.trim();

        if (clean.length() > 1000) {
            throw ApiException.badRequest(
                    "Комментарий settlement: максимум 1000 символов"
            );
        }

        return clean;
    }
}
