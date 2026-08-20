package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentCreateRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionDto;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.CompensationComponentVersionRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * User-facing configuration boundary for generic compensation components.
 *
 * Stable component identity is never physically deleted here.
 * Historical changes are represented by effective-month versions.
 */
@Service
public class CompensationComponentConfigurationService {

    public static final String INVALID_CODE =
            "PAYROLL_COMP_COMPONENT_INVALID";

    private final CompensationComponentRepository components;
    private final CompensationComponentVersionRepository versions;
    private final CompensationComponentResolverService resolver;

    public CompensationComponentConfigurationService(
            CompensationComponentRepository components,
            CompensationComponentVersionRepository versions,
            CompensationComponentResolverService resolver
    ) {
        this.components = components;
        this.versions = versions;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public List<PayrollCompensationComponentVersionDto> history(
            AppUser user
    ) {
        requireUser(user);

        return versions
                .findOwnerHistory(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayrollCompensationComponentVersionDto> effective(
            AppUser user,
            String monthText
    ) {
        requireUser(user);

        YearMonth month =
                parseMonth(
                        monthText
                );

        return resolver
                .resolve(
                        user,
                        month
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PayrollCompensationComponentVersionDto create(
            AppUser user,
            PayrollCompensationComponentCreateRequest request
    ) {
        requireUser(user);

        if (request == null) {
            throw invalid(
                    "Некорректный JSON компонента начисления"
            );
        }

        if (request.version() == null) {
            throw invalid(
                    "Настройки компонента обязательны"
            );
        }

        YearMonth effectiveMonth =
                parseMonth(
                        request.effectiveMonth()
                );

        ParsedVersion parsed =
                parseVersion(
                        request.version()
                );

        CompensationComponent component =
                components.saveAndFlush(
                        new CompensationComponent(
                                user
                        )
                );

        CompensationComponentVersion version;

        try {
            version =
                    new CompensationComponentVersion(
                            component,
                            effectiveMonth.atDay(1),
                            parsed.displayName(),
                            parsed.calculationType(),
                            parsed.calculationBase(),
                            parsed.rateBps(),
                            parsed.amountMinor(),
                            parsed.currencyCode(),
                            parsed.enabled()
                    );
        } catch (IllegalArgumentException ex) {
            throw invalid(
                    ex.getMessage()
            );
        }

        return toDto(
                versions.saveAndFlush(
                        version
                )
        );
    }

    @Transactional
    public PayrollCompensationComponentVersionDto upsertVersion(
            AppUser user,
            Long componentId,
            String monthText,
            PayrollCompensationComponentVersionRequest request
    ) {
        requireUser(user);

        CompensationComponent component =
                requireOwned(
                        user,
                        componentId
                );

        if (request == null) {
            throw invalid(
                    "Некорректный JSON версии компонента"
            );
        }

        YearMonth effectiveMonth =
                parseMonth(
                        monthText
                );

        ParsedVersion parsed =
                parseVersion(
                        request
                );

        CompensationComponentVersion version =
                versions
                        .findByComponentAndEffectiveFrom(
                                component,
                                effectiveMonth.atDay(1)
                        )
                        .orElseGet(() ->
                                new CompensationComponentVersion(
                                        component,
                                        effectiveMonth.atDay(1),
                                        parsed.displayName(),
                                        parsed.calculationType(),
                                        parsed.calculationBase(),
                                        parsed.rateBps(),
                                        parsed.amountMinor(),
                                        parsed.currencyCode(),
                                        parsed.enabled()
                                )
                        );

        if (version.getId() != null) {
            try {
                version.update(
                        parsed.displayName(),
                        parsed.calculationType(),
                        parsed.calculationBase(),
                        parsed.rateBps(),
                        parsed.amountMinor(),
                        parsed.currencyCode(),
                        parsed.enabled()
                );
            } catch (IllegalArgumentException ex) {
                throw invalid(
                        ex.getMessage()
                );
            }
        }

        try {
            return toDto(
                    versions.saveAndFlush(
                            version
                    )
            );
        } catch (IllegalArgumentException ex) {
            throw invalid(
                    ex.getMessage()
            );
        }
    }

    private CompensationComponent requireOwned(
            AppUser user,
            Long componentId
    ) {
        if (componentId == null) {
            throw ApiException.notFound(
                    "Компонент начисления не найден"
            );
        }

        return components
                .findByOwnerAndId(
                        user,
                        componentId
                )
                .orElseThrow(() ->
                        ApiException.notFound(
                                "Компонент начисления не найден"
                        )
                );
    }

    private ParsedVersion parseVersion(
            PayrollCompensationComponentVersionRequest request
    ) {
        if (request == null) {
            throw invalid(
                    "Настройки компонента обязательны"
            );
        }

        if (request.enabled() == null) {
            throw invalid(
                    "Нужно явно указать, включён ли компонент"
            );
        }

        final CalculationType type;
        final CalculationBase base;

        try {
            type =
                    CalculationType.valueOf(
                            normalizeEnum(
                                    request.calculationType()
                            )
                    );

            if (request.calculationBase() == null) {
                base = null;
            } else {
                base =
                        CalculationBase.valueOf(
                                normalizeEnum(
                                        request.calculationBase()
                                )
                        );
            }
        } catch (IllegalArgumentException ex) {
            throw invalid(
                    "Некорректная формула или расчётная база компонента"
            );
        }

        String displayName =
                request.displayName() == null
                        ? ""
                        : request.displayName().trim();

        try {
            /*
             * Probe through the same domain invariants that persistence uses.
             * A detached stable identity is enough because validation does not
             * depend on database identifiers.
             */
            CompensationComponent probeComponent =
                    new CompensationComponent(
                            new AppUser(
                                    "component-validation-probe",
                                    "{noop}unused"
                            )
                    );

            new CompensationComponentVersion(
                    probeComponent,
                    YearMonth.of(
                            2000,
                            1
                    ).atDay(1),
                    displayName,
                    type,
                    base,
                    request.rateBps(),
                    request.amountMinor(),
                    request.currencyCode(),
                    request.enabled()
            );

        } catch (IllegalArgumentException ex) {
            throw invalid(
                    ex.getMessage()
            );
        }

        return new ParsedVersion(
                displayName,
                type,
                base,
                request.rateBps(),
                request.amountMinor(),
                request.currencyCode(),
                request.enabled()
        );
    }

    private YearMonth parseMonth(
            String raw
    ) {
        if (raw == null
                || raw.isBlank()) {
            throw invalid(
                    "Нужно указать месяц действия компонента"
            );
        }

        try {
            return YearMonth.parse(
                    raw.trim()
            );
        } catch (DateTimeParseException ex) {
            throw invalid(
                    "Месяц действия должен быть в формате yyyy-MM"
            );
        }
    }

    private String normalizeEnum(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );
    }

    private PayrollCompensationComponentVersionDto toDto(
            CompensationComponentVersion version
    ) {
        return new PayrollCompensationComponentVersionDto(
                version.getComponent().getId(),
                version.getId(),
                YearMonth.from(
                        version.getEffectiveFrom()
                ).toString(),
                version.getDisplayName(),
                version.getCalculationType().name(),
                version.getCalculationBase() == null
                        ? null
                        : version.getCalculationBase().name(),
                version.getRateBps(),
                version.getAmountMinor(),
                version.getCurrencyCode(),
                version.isEnabled(),
                version.getCreatedAt() == null
                        ? null
                        : version.getCreatedAt().toString(),
                version.getUpdatedAt() == null
                        ? null
                        : version.getUpdatedAt().toString()
        );
    }

    private void requireUser(
            AppUser user
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Compensation component configuration requires user"
            );
        }
    }

    private ApiException invalid(
            String message
    ) {
        return ApiException.badRequest(
                INVALID_CODE,
                message == null
                        ? "Некорректный компонент начисления"
                        : message
        );
    }

    private record ParsedVersion(
            String displayName,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long amountMinor,
            String currencyCode,
            boolean enabled
    ) {}
}
