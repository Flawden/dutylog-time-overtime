package ru.daniil.shifts.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** То же самое, но сразу отсортировано по дате — удобно для Android и отчётов. */
    List<DayEntry> findByOwnerAndDateBetweenOrderByDateAsc(AppUser owner, LocalDate from, LocalDate to);

    /** Все записи владельца от первой до последней — для совместимости и общих отчётов. */
    List<DayEntry> findByOwnerOrderByDateAsc(AppUser owner);

    /** Conservative preflight count for bounded note export (blank notes count toward the cap). */
    long countByOwnerAndNoteIsNotNull(AppUser owner);

    /**
     * Только непустые заметки владельца. fetch join заранее загружает название смены,
     * чтобы ZIP можно было безопасно стримить уже вне транзакции.
     */
    @Query("""
            select d from DayEntry d
            left join fetch d.shiftType
            where d.owner = :owner
              and d.note is not null
            order by d.date asc
            """)
    List<DayEntry> findNotesForExport(@Param("owner") AppUser owner);

    /** Записи, ссылающиеся на тип смены — нужно при удалении типа. */
    List<DayEntry> findByShiftType(ShiftType shiftType);
}
