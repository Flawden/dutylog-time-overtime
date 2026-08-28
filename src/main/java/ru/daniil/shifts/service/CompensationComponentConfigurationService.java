package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentCreateRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionDto;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.CompensationComponentVersionRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
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

        PayrollEarningKind createEarningKind =
                parsed.earningKindUpdate()
                        .explicit()
                        ? parsed.earningKindUpdate()
                                .value()
                        : null;

        validateLocalEligibleBaseTarget(
                parsed.calculationType(),
                parsed.calculationBase(),
                createEarningKind
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

            /*
             * A stable component has no previous semantic identity.
             * Missing kind therefore starts explicitly UNCLASSIFIED.
             */
            version.updateEarningKind(
                    createEarningKind
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

        /*
         * Compatibility-safe semantic behavior:
         *
         * explicit kind       => set it;
         * explicit UNCLASSIFIED => clear it;
         * omitted on existing version => preserve it;
         * omitted on new effective version => inherit prior effective kind.
         *
         * Resolve and validate the semantic target before mutating an
         * already-managed version so an invalid LOCAL_ELIGIBLE_EARNINGS
         * request fails without leaving transient cross-field mutation.
         */
        PayrollEarningKind targetEarningKind =
                resolvedEarningKind(
                        user,
                        component,
                        effectiveMonth.atDay(1),
                        version,
                        parsed.earningKindUpdate()
                );

        validateLocalEligibleBaseTarget(
                parsed.calculationType(),
                parsed.calculationBase(),
                targetEarningKind
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
            version.updateEarningKind(
                    targetEarningKind
            );

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

    private PayrollEarningKind resolvedEarningKind(
            AppUser user,
            CompensationComponent component,
            LocalDate effectiveFrom,
            CompensationComponentVersion targetVersion,
            ParsedEarningKind update
    ) {
        if (update == null) {
            throw new IllegalArgumentException(
                    "Semantic earning kind update is missing"
            );
        }

        if (update.explicit()) {
            return update.value();
        }

        /*
         * An old client editing an already-existing effective version must
         * never erase semantic identity merely because it does not know about
         * the new field.
         */
        if (targetVersion != null
                && targetVersion.getId() != null) {
            return targetVersion.getEarningKind();
        }

        /*
         * An old client creating the next effective version inherits the
         * immediately previous machine identity.
         *
         * Query ordering is component ASC, effective DESC, id DESC.
         */
        Long componentId =
                component == null
                        ? null
                        : component.getId();

        if (componentId == null) {
            throw new IllegalArgumentException(
                    "Compensation component identity is incomplete"
            );
        }

        LocalDate lookupDate =
                effectiveFrom.minusDays(
                        1
                );

        for (
                CompensationComponentVersion candidate
                : versions.findOwnerHistoryAtOrBefore(
                        user,
                        lookupDate
                )
        ) {
            if (candidate == null
                    || candidate.getComponent() == null
                    || candidate.getComponent().getId() == null) {
                continue;
            }

            if (candidate.getComponent()
                    .getId()
                    .equals(
                            componentId
                    )) {
                return candidate.getEarningKind();
            }
        }

        return null;
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

        ParsedEarningKind earningKindUpdate =
                parseEarningKindUpdate(
                        request.earningKind()
                );

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
                earningKindUpdate,
                type,
                base,
                request.rateBps(),
                request.amountMinor(),
                request.currencyCode(),
                request.enabled()
        );
    }

    private void validateLocalEligibleBaseTarget(
            CalculationType calculationType,
            CalculationBase calculationBase,
            PayrollEarningKind earningKind
    ) {
        if (calculationType
                == CalculationType.PERCENT_OF_BASE
                && calculationBase
                == CalculationBase.LOCAL_ELIGIBLE_EARNINGS
                && earningKind
                != PayrollEarningKind.MONTHLY_BONUS
                && earningKind
                != PayrollEarningKind.REGIONAL_COEFFICIENT) {
            throw invalid(
                    "LOCAL_ELIGIBLE_EARNINGS допустима только для MONTHLY_BONUS и REGIONAL_COEFFICIENT"
            );
        }
    }

    private ParsedEarningKind parseEarningKindUpdate(
            String raw
    ) {
        /*
         * Missing field is deliberately different from explicit
         * UNCLASSIFIED so pre-8A3D1E clients cannot erase semantic identity.
         */
        if (raw == null) {
            return new ParsedEarningKind(
                    false,
                    null
            );
        }

        String normalized =
                normalizeEnum(
                        raw
                );

        if ("UNCLASSIFIED".equals(
                normalized
        )) {
            return new ParsedEarningKind(
                    true,
                    null
            );
        }

        final PayrollEarningKind earningKind;

        try {
            earningKind =
                    PayrollEarningKind.valueOf(
                            normalized
                    );
        } catch (IllegalArgumentException ex) {
            throw invalid(
                    "Некорректный семантический тип компонента"
            );
        }

        if (!earningKind
                .isGenericCompensationComponentKind()) {
            throw invalid(
                    "Этот тип выплаты нельзя создавать generic-компонентом"
            );
        }

        return new ParsedEarningKind(
                true,
                earningKind
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
                version.getEarningKind() == null
                        ? null
                        : version.getEarningKind().name(),
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

    private record ParsedEarningKind(
            boolean explicit,
            PayrollEarningKind value
    ) {}

    private record ParsedVersion(
            String displayName,
            ParsedEarningKind earningKindUpdate,
            CalculationType calculationType,
            CalculationBase calculationBase,
            Integer rateBps,
            Long amountMinor,
            String currencyCode,
            boolean enabled
    ) {}
}
