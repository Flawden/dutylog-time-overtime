package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.repo.CompensationComponentVersionRepository;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves one effective version of each stable compensation component
 * for one Payroll month.
 *
 * Disabled versions remain part of resolution deliberately:
 * "disabled from this month" is a historical state, not missing history.
 */
@Service
public class CompensationComponentResolverService {

    private final CompensationComponentVersionRepository versions;

    public CompensationComponentResolverService(
            CompensationComponentVersionRepository versions
    ) {
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<CompensationComponentVersion> resolve(
            AppUser user,
            YearMonth month
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Compensation component resolver requires user"
            );
        }

        if (month == null) {
            throw new IllegalArgumentException(
                    "Compensation component resolver requires month"
            );
        }

        /*
         * Repository order is:
         *
         * component id ASC,
         * effective month DESC,
         * version id DESC.
         *
         * Therefore the first row observed for one stable component
         * is exactly its effective version for the requested month.
         */
        Map<Long, CompensationComponentVersion> effective =
                new LinkedHashMap<>();

        for (
                CompensationComponentVersion version
                : versions.findOwnerHistoryAtOrBefore(
                        user,
                        month.atDay(1)
                )
        ) {
            if (version == null
                    || version.getComponent() == null
                    || version.getComponent().getId() == null) {
                throw new IllegalStateException(
                        "Persisted compensation component identity is incomplete"
                );
            }

            effective.putIfAbsent(
                    version.getComponent().getId(),
                    version
            );
        }

        return List.copyOf(
                effective.values()
        );
    }
}
