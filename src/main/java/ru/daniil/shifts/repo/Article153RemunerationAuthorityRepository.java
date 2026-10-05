package ru.daniil.shifts.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.*;
import java.time.LocalDate;
import java.util.Optional;
public interface Article153RemunerationAuthorityRepository extends JpaRepository<Article153RemunerationAuthority,Long> {
    Optional<Article153RemunerationAuthority> findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(AppUser owner,LocalDate month);
    Optional<Article153RemunerationAuthority> findByIdAndOwner(Long id,AppUser owner);
}
