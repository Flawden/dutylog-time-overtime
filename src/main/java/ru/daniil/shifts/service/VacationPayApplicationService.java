package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Application boundary for one annual-vacation pay calculation.
 *
 * <p>O/Q owns application wiring only: P6 reference authority -> J5 ordered
 * fallback -> canonical P7/P8 basis authorities -> N vacation-pay orchestrator.
 * It deliberately does not calculate paragraph-7 calendar facts, paragraph-8
 * formula facts, average-daily money, payable vacation days or final vacation
 * money.</p>
 *
 * <p>J5 authority suppliers remain lazy according to J5. Production basis
 * suppliers are created only after J5 selection and stay lazy through N, so N
 * still owns the L-ready gate before K can evaluate them. The explicit K-basis
 * supplier overload remains as a deterministic test/integration seam.</p>
 */
@Service
public class VacationPayApplicationService {
    public static final String RULE_ID = "DUTYLOG_VACATION_PAY_APPLICATION";

    private final Paragraph6Resolver paragraph6Resolver;
    private final OrderedFallbackResolver orderedFallbackResolver;
    private final ReferenceCalculator referenceCalculator;
    private final VacationResolver vacationResolver;
    private final Paragraph7AuthorityResolver paragraph7AuthorityResolver;
    private final Paragraph8AuthorityResolver paragraph8AuthorityResolver;
    private final Paragraph7CalendarBasisResolver paragraph7CalendarBasisResolver;
    private final Paragraph8FormulaBasisResolver paragraph8FormulaBasisResolver;

    @Autowired
    public VacationPayApplicationService(
            AverageEarningsParagraph6ReferenceResolver paragraph6,
            VacationAveragePrimaryCalculationService referenceCalculation,
            VacationPayOrchestrator vacationPay,
            AverageEarningsParagraph7PreEventAccruedWageAuthorityService paragraph7Authority,
            AverageEarningsParagraph8TariffSalaryAuthorityService paragraph8Authority,
            AverageEarningsParagraph7CalendarBasisAuthorityService paragraph7CalendarBasis,
            AverageEarningsParagraph8VacationFormulaBasisAuthorityService paragraph8FormulaBasis
    ) {
        this(
                paragraph6::resolve,
                AverageEarningsOrderedFallbackResolver::resolve,
                (user, eventDate, window, discoveryThroughMonth, provenNoPayrollMonths) ->
                        referenceCalculation.calculate(
                                user,
                                eventDate,
                                window,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        ),
                vacationPay::resolve,
                paragraph7Authority::resolve,
                paragraph8Authority::resolve,
                paragraph7CalendarBasis::resolve,
                paragraph8FormulaBasis::resolve
        );
    }

    VacationPayApplicationService(
            Paragraph6Resolver paragraph6Resolver,
            OrderedFallbackResolver orderedFallbackResolver,
            ReferenceCalculator referenceCalculator,
            VacationResolver vacationResolver
    ) {
        this(
                paragraph6Resolver,
                orderedFallbackResolver,
                referenceCalculator,
                vacationResolver,
                VacationPayApplicationService::unconfiguredParagraph7CalendarBasis,
                VacationPayApplicationService::unconfiguredParagraph8FormulaBasis
        );
    }

    VacationPayApplicationService(
            Paragraph6Resolver paragraph6Resolver,
            OrderedFallbackResolver orderedFallbackResolver,
            ReferenceCalculator referenceCalculator,
            VacationResolver vacationResolver,
            Paragraph7CalendarBasisResolver paragraph7CalendarBasisResolver,
            Paragraph8FormulaBasisResolver paragraph8FormulaBasisResolver
    ) {
        this(
                paragraph6Resolver,
                orderedFallbackResolver,
                referenceCalculator,
                vacationResolver,
                VacationPayApplicationService::unconfiguredParagraph7Authority,
                VacationPayApplicationService::unconfiguredParagraph8Authority,
                paragraph7CalendarBasisResolver,
                paragraph8FormulaBasisResolver
        );
    }

    VacationPayApplicationService(
            Paragraph6Resolver paragraph6Resolver,
            OrderedFallbackResolver orderedFallbackResolver,
            ReferenceCalculator referenceCalculator,
            VacationResolver vacationResolver,
            Paragraph7AuthorityResolver paragraph7AuthorityResolver,
            Paragraph8AuthorityResolver paragraph8AuthorityResolver,
            Paragraph7CalendarBasisResolver paragraph7CalendarBasisResolver,
            Paragraph8FormulaBasisResolver paragraph8FormulaBasisResolver
    ) {
        this.paragraph6Resolver = Objects.requireNonNull(
                paragraph6Resolver,
                "Vacation pay application requires paragraph-6 resolver"
        );
        this.orderedFallbackResolver = Objects.requireNonNull(
                orderedFallbackResolver,
                "Vacation pay application requires ordered-fallback resolver"
        );
        this.referenceCalculator = Objects.requireNonNull(
                referenceCalculator,
                "Vacation pay application requires selected-reference calculator"
        );
        this.vacationResolver = Objects.requireNonNull(
                vacationResolver,
                "Vacation pay application requires vacation-pay orchestrator"
        );
        this.paragraph7AuthorityResolver = Objects.requireNonNull(
                paragraph7AuthorityResolver,
                "Vacation pay application requires canonical paragraph-7 authority"
        );
        this.paragraph8AuthorityResolver = Objects.requireNonNull(
                paragraph8AuthorityResolver,
                "Vacation pay application requires canonical paragraph-8 authority"
        );
        this.paragraph7CalendarBasisResolver = Objects.requireNonNull(
                paragraph7CalendarBasisResolver,
                "Vacation pay application requires paragraph-7 calendar-basis authority"
        );
        this.paragraph8FormulaBasisResolver = Objects.requireNonNull(
                paragraph8FormulaBasisResolver,
                "Vacation pay application requires paragraph-8 formula-basis authority"
        );
    }

    /**
     * Canonical production entry point. J5 P7/P8 authorities and K P7/P8 basis
     * suppliers are all owned by Spring application wiring. Every later branch
     * remains lazy behind ordered fallback / N so an authority is never read
     * before the preceding legal basis is proven exhausted.
     */
    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        List<YearMonth> zeroProofs = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Vacation pay application requires explicit no-Payroll proofs"
        ));
        return resolveInternal(
                user,
                eventDate,
                absencePeriodId,
                discoveryThroughMonth,
                zeroProofs,
                () -> canonicalParagraph7Authority(
                        user,
                        eventDate,
                        discoveryThroughMonth,
                        zeroProofs
                ),
                () -> canonicalParagraph8Authority(user, eventDate),
                ordered -> () -> canonicalParagraph7CalendarBasis(user, eventDate),
                ordered -> () -> canonicalParagraph8FormulaBasis(
                        user,
                        eventDate,
                        ordered
                )
        );
    }

    /**
     * Explicit J5 authority supplier seam retained for focused tests and
     * controlled integration callers. Canonical K-basis wiring remains owned
     * by the application service.
     */
    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths,
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>
                    paragraph7AuthoritySupplier,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>
                    paragraph8AuthoritySupplier
    ) {
        return resolveInternal(
                user,
                eventDate,
                absencePeriodId,
                discoveryThroughMonth,
                provenNoPayrollMonths,
                paragraph7AuthoritySupplier,
                paragraph8AuthoritySupplier,
                ordered -> () -> canonicalParagraph7CalendarBasis(user, eventDate),
                ordered -> () -> canonicalParagraph8FormulaBasis(
                        user,
                        eventDate,
                        ordered
                )
        );
    }

    /**
     * Explicit K-basis supplier seam retained for focused tests and controlled
     * integration callers. Application code forwards both suppliers untouched.
     */
    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths,
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>
                    paragraph7AuthoritySupplier,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>
                    paragraph8AuthoritySupplier,
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>
                    paragraph7CalendarBasisSupplier,
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>
                    paragraph8FormulaBasisSupplier
    ) {
        Objects.requireNonNull(
                paragraph7CalendarBasisSupplier,
                "Vacation pay application requires lazy paragraph-7 calendar basis"
        );
        Objects.requireNonNull(
                paragraph8FormulaBasisSupplier,
                "Vacation pay application requires lazy paragraph-8 formula basis"
        );
        return resolveInternal(
                user,
                eventDate,
                absencePeriodId,
                discoveryThroughMonth,
                provenNoPayrollMonths,
                paragraph7AuthoritySupplier,
                paragraph8AuthoritySupplier,
                ordered -> paragraph7CalendarBasisSupplier,
                ordered -> paragraph8FormulaBasisSupplier
        );
    }

    private Resolution resolveInternal(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths,
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>
                    paragraph7AuthoritySupplier,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>
                    paragraph8AuthoritySupplier,
            Paragraph7BasisSupplierFactory paragraph7BasisSupplierFactory,
            Paragraph8BasisSupplierFactory paragraph8BasisSupplierFactory
    ) {
        Objects.requireNonNull(user, "Vacation pay application requires user");
        Objects.requireNonNull(eventDate, "Vacation pay application requires event date");
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Vacation pay application requires discovery-through month"
        );
        List<YearMonth> zeroProofs = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Vacation pay application requires explicit no-Payroll proofs"
        ));
        Objects.requireNonNull(
                paragraph7AuthoritySupplier,
                "Vacation pay application requires lazy paragraph-7 authority"
        );
        Objects.requireNonNull(
                paragraph8AuthoritySupplier,
                "Vacation pay application requires lazy paragraph-8 authority"
        );
        Objects.requireNonNull(
                paragraph7BasisSupplierFactory,
                "Vacation pay application requires paragraph-7 basis supplier factory"
        );
        Objects.requireNonNull(
                paragraph8BasisSupplierFactory,
                "Vacation pay application requires paragraph-8 basis supplier factory"
        );

        AverageEarningsParagraph6ReferenceResolver.Resolution paragraph6 =
                Objects.requireNonNull(
                        paragraph6Resolver.resolve(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                zeroProofs
                        ),
                        "Paragraph-6 application authority returned null"
                );

        AverageEarningsOrderedFallbackResolver.Resolution orderedFallback =
                Objects.requireNonNull(
                        orderedFallbackResolver.resolve(
                                paragraph6,
                                paragraph7AuthoritySupplier,
                                paragraph8AuthoritySupplier
                        ),
                        "Ordered vacation fallback returned null"
                );

        Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>
                paragraph7CalendarBasisSupplier = Objects.requireNonNull(
                        paragraph7BasisSupplierFactory.create(orderedFallback),
                        "Paragraph-7 basis supplier factory returned null"
                );
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>
                paragraph8FormulaBasisSupplier = Objects.requireNonNull(
                        paragraph8BasisSupplierFactory.create(orderedFallback),
                        "Paragraph-8 basis supplier factory returned null"
                );

        Supplier<VacationAveragePrimaryCalculationService.Resolution>
                selectedReferenceSupplier = () -> {
                    AverageEarningsReferenceWindow selected =
                            Objects.requireNonNull(
                                    orderedFallback.selectedReferenceWindow(),
                                    "Selected reference branch lost J5 reference window"
                            );
                    return Objects.requireNonNull(
                            referenceCalculator.calculate(
                                    user,
                                    eventDate,
                                    selected,
                                    discoveryThroughMonth,
                                    zeroProofs
                            ),
                            "Selected vacation reference calculation returned null"
                    );
                };

        VacationPayOrchestrator.Resolution vacationPay =
                Objects.requireNonNull(
                        vacationResolver.resolve(
                                user,
                                eventDate,
                                absencePeriodId,
                                orderedFallback,
                                selectedReferenceSupplier,
                                paragraph7CalendarBasisSupplier,
                                paragraph8FormulaBasisSupplier
                        ),
                        "Vacation pay orchestrator returned null"
                );

        return new Resolution(
                eventDate,
                YearMonth.from(eventDate),
                absencePeriodId,
                discoveryThroughMonth,
                zeroProofs,
                paragraph6,
                orderedFallback,
                vacationPay
        );
    }

    private AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution
            canonicalParagraph7Authority(
                    AppUser user,
                    LocalDate eventDate,
                    YearMonth discoveryThroughMonth,
                    List<YearMonth> provenNoPayrollMonths
            ) {
        return Objects.requireNonNull(
                paragraph7AuthorityResolver.resolve(
                        user,
                        eventDate,
                        discoveryThroughMonth,
                        provenNoPayrollMonths
                ),
                "Canonical paragraph-7 accrued-wage authority returned null"
        );
    }

    private AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
            canonicalParagraph8Authority(
                    AppUser user,
                    LocalDate eventDate
            ) {
        return Objects.requireNonNull(
                paragraph8AuthorityResolver.resolve(user, eventDate),
                "Canonical paragraph-8 tariff/salary authority returned null"
        );
    }

    private VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis
            canonicalParagraph7CalendarBasis(
                    AppUser user,
                    LocalDate eventDate
            ) {
        AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution resolution =
                Objects.requireNonNull(
                        paragraph7CalendarBasisResolver.resolve(user, eventDate),
                        "Paragraph-7 calendar-basis authority returned null"
                );
        return resolution.ready() ? resolution.basis() : null;
    }

    private VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis
            canonicalParagraph8FormulaBasis(
                    AppUser user,
                    LocalDate eventDate,
                    AverageEarningsOrderedFallbackResolver.Resolution orderedFallback
            ) {
        AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution resolution =
                Objects.requireNonNull(
                        paragraph8FormulaBasisResolver.resolve(
                                user,
                                eventDate,
                                orderedFallback.paragraph8Authority()
                        ),
                        "Paragraph-8 formula-basis authority returned null"
                );
        return resolution.ready() ? resolution.basis() : null;
    }

    private static AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution
            unconfiguredParagraph7Authority(
                    AppUser user,
                    LocalDate eventDate,
                    YearMonth discoveryThroughMonth,
                    List<YearMonth> provenNoPayrollMonths
            ) {
        throw new IllegalStateException(
                "Canonical paragraph-7 accrued-wage authority is unavailable in test seam"
        );
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
            unconfiguredParagraph8Authority(
                    AppUser user,
                    LocalDate eventDate
            ) {
        throw new IllegalStateException(
                "Canonical paragraph-8 tariff/salary authority is unavailable in test seam"
        );
    }

    private static AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution
            unconfiguredParagraph7CalendarBasis(
                    AppUser user,
                    LocalDate eventDate
            ) {
        throw new IllegalStateException(
                "Canonical paragraph-7 calendar-basis authority is unavailable in test seam"
        );
    }

    private static AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution
            unconfiguredParagraph8FormulaBasis(
                    AppUser user,
                    LocalDate eventDate,
                    AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8
            ) {
        throw new IllegalStateException(
                "Canonical paragraph-8 formula-basis authority is unavailable in test seam"
        );
    }

    @FunctionalInterface
    interface Paragraph6Resolver {
        AverageEarningsParagraph6ReferenceResolver.Resolution resolve(
                AppUser user,
                LocalDate eventDate,
                YearMonth discoveryThroughMonth,
                List<YearMonth> provenNoPayrollMonths
        );
    }

    @FunctionalInterface
    interface OrderedFallbackResolver {
        AverageEarningsOrderedFallbackResolver.Resolution resolve(
                AverageEarningsParagraph6ReferenceResolver.Resolution paragraph6,
                Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution>
                        paragraph7Supplier,
                Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution>
                        paragraph8Supplier
        );
    }

    @FunctionalInterface
    interface ReferenceCalculator {
        VacationAveragePrimaryCalculationService.Resolution calculate(
                AppUser user,
                LocalDate eventDate,
                AverageEarningsReferenceWindow selectedWindow,
                YearMonth discoveryThroughMonth,
                List<YearMonth> provenNoPayrollMonths
        );
    }

    @FunctionalInterface
    interface Paragraph7AuthorityResolver {
        AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution resolve(
                AppUser user,
                LocalDate eventDate,
                YearMonth discoveryThroughMonth,
                List<YearMonth> provenNoPayrollMonths
        );
    }

    @FunctionalInterface
    interface Paragraph8AuthorityResolver {
        AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution resolve(
                AppUser user,
                LocalDate eventDate
        );
    }

    @FunctionalInterface
    interface Paragraph7CalendarBasisResolver {
        AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution resolve(
                AppUser user,
                LocalDate eventDate
        );
    }

    @FunctionalInterface
    interface Paragraph8FormulaBasisResolver {
        AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution resolve(
                AppUser user,
                LocalDate eventDate,
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
                        paragraph8Authority
        );
    }

    @FunctionalInterface
    interface Paragraph7BasisSupplierFactory {
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> create(
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback
        );
    }

    @FunctionalInterface
    interface Paragraph8BasisSupplierFactory {
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> create(
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback
        );
    }

    @FunctionalInterface
    interface VacationResolver {
        VacationPayOrchestrator.Resolution resolve(
                AppUser user,
                LocalDate eventDate,
                Long absencePeriodId,
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                Supplier<VacationAveragePrimaryCalculationService.Resolution>
                        referenceCalculationSupplier,
                Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>
                        paragraph7CalendarBasisSupplier,
                Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>
                        paragraph8FormulaBasisSupplier
        );
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            Long requestedAbsencePeriodId,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths,
            AverageEarningsParagraph6ReferenceResolver.Resolution paragraph6Authority,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            VacationPayOrchestrator.Resolution vacationPay
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Vacation application result requires event date");
            Objects.requireNonNull(eventMonth, "Vacation application result requires event month");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "Vacation application result requires discovery-through month"
            );
            provenNoPayrollMonths = List.copyOf(Objects.requireNonNull(
                    provenNoPayrollMonths,
                    "Vacation application result requires no-Payroll proofs"
            ));
            Objects.requireNonNull(
                    paragraph6Authority,
                    "Vacation application result requires paragraph-6 provenance"
            );
            Objects.requireNonNull(
                    orderedFallback,
                    "Vacation application result requires J5 provenance"
            );
            Objects.requireNonNull(
                    vacationPay,
                    "Vacation application result requires N provenance"
            );
            if (vacationPay.ready()
                    && (!paragraph6Authority.ready() || !orderedFallback.ready())) {
                throw new IllegalArgumentException(
                        "Ready vacation application result requires ready P6 and J5 provenance"
                );
            }
            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !eventDate.equals(paragraph6Authority.eventDate())
                    || !eventMonth.equals(paragraph6Authority.eventMonth())
                    || !discoveryThroughMonth.equals(
                            paragraph6Authority.discoveryThroughMonth()
                    )
                    || !eventDate.equals(orderedFallback.eventDate())
                    || !eventMonth.equals(orderedFallback.eventMonth())
                    || !eventDate.equals(vacationPay.eventDate())
                    || !eventMonth.equals(vacationPay.eventMonth())
                    || !Objects.equals(
                            requestedAbsencePeriodId,
                            vacationPay.requestedAbsencePeriodId()
                    )) {
                throw new IllegalArgumentException(
                        "Vacation application result authority identity is inconsistent"
                );
            }
        }

        public boolean ready() {
            return vacationPay.ready();
        }

        public AverageEarningsOrderedFallbackResolver.Selection selectedBasis() {
            return orderedFallback.ready() ? orderedFallback.selection() : null;
        }

        public VacationPayOrchestrator.BlockingStage blockingStage() {
            return vacationPay.blockingStage();
        }

        public String blockingReason() {
            return vacationPay.blockingReason();
        }

        public String upstreamBlockingReason() {
            return vacationPay.upstreamBlockingReason();
        }

        public String currencyCode() {
            return vacationPay.currencyCode();
        }

        public Long vacationPayMinor() {
            return vacationPay.vacationPayMinor();
        }

        public int payableCalendarDays() {
            return vacationPay.payableCalendarDays();
        }
    }
}
