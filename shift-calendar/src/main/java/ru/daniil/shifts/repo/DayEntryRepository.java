package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DayEntryRepository extends JpaRepository<DayEntry, Long> {

    Optional<DayEntry> findByOwnerAndDate(AppUser owner, LocalDate date);

    /** Все записи пользователя в диапазоне дат включительно — для загрузки месяца. */
    List<DayEntry> findByOwnerAndDateBetween(AppUser owner, LocalDate from, LocalDate to);

    /** Записи, ссылающиеся на тип смены — нужно при удалении типа. */
    List<DayEntry> findByShiftType(ShiftType shiftType);
}
