package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayPricingTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayPricingTermRepository
        extends JpaRepository<PayPricingTerm, Long> {

    Optional<PayPricingTerm>
    findByOwnerAndEffectiveFrom(
            AppUser owner,
            LocalDate effectiveFrom
    );

    Optional<PayPricingTerm>
    findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            AppUser owner,
            LocalDate date
    );

    List<PayPricingTerm>
    findByOwnerOrderByEffectiveFromDescIdDesc(
            AppUser owner
    );
}
