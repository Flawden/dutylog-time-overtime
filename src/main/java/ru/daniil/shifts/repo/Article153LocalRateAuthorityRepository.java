package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153LocalRateAuthority;
import ru.daniil.shifts.model.PayPricingTerm;

import java.util.Optional;

public interface Article153LocalRateAuthorityRepository
        extends JpaRepository<Article153LocalRateAuthority, Long> {

    Optional<Article153LocalRateAuthority> findByOwnerAndPricingTerm(
            AppUser owner,
            PayPricingTerm pricingTerm
    );
}
