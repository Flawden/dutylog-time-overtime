package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.CompensationCalculationService.Result;
import ru.daniil.shifts.service.CompensationComponentCalculationService.CalculatedLine;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Context;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Projection;
import ru.daniil.shifts.service.PayrollCompensationComponentPreviewService.ComponentPreview;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;
import ru.daniil.shifts.service.PayrollSettlementPreviewService.SettlementPreview;
import ru.daniil.shifts.service.PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Native Payroll money layer. Time truth comes only from TimeCompensationService;
 * compensation terms only answer how that already-canonical time should be priced.
 */
@Service
public class PayrollService {
    private final PayrollSettingsRepository settings;
    private final CompensationTermRepository compensationTerms;
    private final PayrollAdjustmentRepository adjustments;
    private final PayrollSnapshotRepository snapshots;
    private final TimeAccountingPeriodRepository accountingPeriods;
    private final TimeCompensationService timeCompensation;
    private final LedgerIntegrityService ledgerIntegrity;
    private final ProductionCalendarService productionCalendar;
    private final CompensationCalculationService calculation;
    private final PayrollSettlementPreviewService settlementPricing;
    private final PayrollOrdinaryPremiumPreviewService ordinaryPremiumPricing;

    /*
     * 7A3B collaborators are setter-injected so historical isolated tests
     * that construct the pre-7A3B PayrollService directly remain source-compatible.
     */
    private PayrollCompensationComponentPreviewService componentPricing;
    private PayrollSnapshotComponentLineRepository snapshotComponentLines;

    /*
     * 8A3D1C remains setter-injected for the same source-compatibility reason:
     * many historical isolated tests construct PayrollService directly.
     *
     * The Spring application context treats this collaborator as required.
     */
    private PayrollSemanticFreezeService semanticEarningFreeze;

    /*
     * 8A4E2B2 keeps the same compatibility pattern: real Spring runtime
     * requires the collaborator, while historical direct-construction tests
     * retain aggregate-only BASE_PAY freeze semantics.
     */
    private PayrollBasePaySemanticProvenance basePaySemanticProvenance;

    /*
     * 8A4E2B3A remains setter-injected for historical direct-construction
     * compatibility. Real Spring runtime requires it and can therefore expand
     * only machine-proven generic component source lines.
     */
    private PayrollCompensationComponentSemanticProvenance
            componentSemanticProvenance;

    /*
     * 8A4E2B3B explicit COMBINATION episode facts are also setter-injected
     * for historical direct-construction compatibility. Real Spring runtime
     * requires the authority and therefore never fabricates source periods
     * from posting month or generic component configuration.
     */
    private PayrollCombinationEpisodeFactService
            combinationEpisodeFacts;

    /*
     * 8A4E2B3C2 explicit REGIONAL source facts stay separate from the
     * LOCAL_ELIGIBLE_EARNINGS money formula. Real Spring runtime requires
     * this authority; historical direct-construction tests remain compatible.
     */
    private PayrollRegionalCoefficientSourceFactService
            regionalCoefficientSourceFacts;

    public PayrollService(PayrollSettingsRepository settings,
                          CompensationTermRepository compensationTerms,
                          PayrollAdjustmentRepository adjustments,
                          PayrollSnapshotRepository snapshots,
                          TimeAccountingPeriodRepository accountingPeriods,
                          TimeCompensationService timeCompensation,
                          LedgerIntegrityService ledgerIntegrity,
                          ProductionCalendarService productionCalendar,
                          CompensationCalculationService calculation,
                          PayrollSettlementPreviewService settlementPricing,
                          PayrollOrdinaryPremiumPreviewService ordinaryPremiumPricing) {
        this.settings = settings; this.compensationTerms = compensationTerms; this.adjustments = adjustments;
        this.snapshots = snapshots; this.accountingPeriods = accountingPeriods; this.timeCompensation = timeCompensation;
        this.ledgerIntegrity = ledgerIntegrity; this.productionCalendar = productionCalendar; this.calculation = calculation;
        this.settlementPricing = settlementPricing;
        this.ordinaryPremiumPricing = ordinaryPremiumPricing;
    }

    @Autowired
    void configureCompensationComponents(
            PayrollCompensationComponentPreviewService componentPricing,
            PayrollSnapshotComponentLineRepository snapshotComponentLines
    ) {
        this.componentPricing = componentPricing;
        this.snapshotComponentLines = snapshotComponentLines;
    }

    @Autowired
    void configureSemanticEarningFreeze(
            PayrollSemanticFreezeService semanticEarningFreeze
    ) {
        this.semanticEarningFreeze =
                semanticEarningFreeze;
    }

    @Autowired
    void configureBasePaySemanticProvenance(
            PayrollBasePaySemanticProvenance basePaySemanticProvenance
    ) {
        this.basePaySemanticProvenance =
                basePaySemanticProvenance;
    }

    @Autowired
    void configureCompensationComponentSemanticProvenance(
            PayrollCompensationComponentSemanticProvenance
                    componentSemanticProvenance
    ) {
        this.componentSemanticProvenance =
                componentSemanticProvenance;
    }

    @Autowired
    void configureCombinationEpisodeFacts(
            PayrollCombinationEpisodeFactService combinationEpisodeFacts
    ) {
        this.combinationEpisodeFacts =
                combinationEpisodeFacts;
    }

    @Autowired
    void configureRegionalCoefficientSourceFacts(
            PayrollRegionalCoefficientSourceFactService regionalCoefficientSourceFacts
    ) {
        this.regionalCoefficientSourceFacts =
                regionalCoefficientSourceFacts;
    }

    @Transactional
    public PayrollPeriodDto period(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        PayrollSettings legacySettings = ensureSettings(user);
        return buildPeriod(user, month, legacySettings);
    }

    /** Compatibility adapter for pre-v27.46 clients. New UI writes compensation terms instead. */
    @Transactional
    public PayrollSettingsDto updateSettings(AppUser user, PayrollSettingsUpdateRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        String currency = normalizeCurrency(request.currencyCode());
        long rate = request.hourlyRateMinor() == null ? -1L : request.hourlyRateMinor();
        if (rate < 0 || rate > 1_000_000_000L) {
            throw ApiException.badRequest("PAYROLL_RATE_INVALID", "Некорректная почасовая ставка");
        }
        PayrollSettings value = ensureSettings(user);
        value.update(currency, rate);
        PayrollSettings saved = settings.saveAndFlush(value);
        if (rate > 0) upsertTerm(user, currentCompensationMonth(user), "HOURLY", currency, rate, null);
        return toSettings(saved);
    }

    @Transactional
    public PayrollCompensationTermDto upsertCompensationTerm(AppUser user, String monthText,
                                                               PayrollCompensationTermRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        YearMonth month = parseMonth(monthText);
        String mode = normalizePayMode(request.payMode());
        String currency = normalizeCurrency(request.currencyCode());
        Long hourly = request.hourlyRateMinor();
        Long salary = request.monthlySalaryMinor();
        validateTermShape(mode, hourly, salary);
        return toTerm(upsertTerm(user, month.atDay(1), mode, currency, hourly, salary));
    }

    @Transactional
    public void deleteCompensationTerm(AppUser user, String monthText) {
        LocalDate effective = parseMonth(monthText).atDay(1);
        compensationTerms.findByOwnerAndEffectiveFrom(user, effective).ifPresent(compensationTerms::delete);
        compensationTerms.flush();
    }

    @Transactional
    public PayrollAdjustmentDto addAdjustment(AppUser user, PayrollAdjustmentRequest request) {
        if (request == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        YearMonth month = parseMonth(request.month());
        requireClosedPeriod(user, month, true);
        String type = normalizeAdjustmentType(request.adjustmentType());
        long amount = request.amountMinor() == null ? 0L : request.amountMinor();
        if (amount < 1L || amount > 1_000_000_000_000L) {
            throw ApiException.badRequest("PAYROLL_ADJUSTMENT_INVALID", "Некорректная сумма корректировки");
        }
        String title = cleanRequired(request.title(), 120, "Название корректировки обязательно");
        String note = cleanOptional(request.note(), 500);
        PayrollAdjustment saved = adjustments.saveAndFlush(new PayrollAdjustment(user, month.atDay(1), type, amount, title, note));
        return toAdjustment(saved);
    }

    @Transactional
    public PayrollSnapshotDto calculate(AppUser user, String monthText) {
        YearMonth month = parseMonth(monthText);
        TimeAccountingPeriod period = requireClosedPeriod(user, month, true);
        LedgerIntegrityDto integrity = ledgerIntegrity.inspect(user, month.atDay(1), month.atEndOfMonth());
        if (!integrity.healthy()) {
            throw ApiException.conflict("LEDGER_INTEGRITY_FAILED", "Расчёт зарплаты заблокирован: сначала исправь расхождения журнала");
        }
        CompensationTerm term = effectiveTerm(user, month);
        if (term == null) throw ApiException.conflict("PAYROLL_COMPENSATION_REQUIRED", "Сначала укажи способ оплаты");
        ProductionCalendarMonthDto production = productionCalendar.month(user, month.toString());
        if ("SALARY".equals(term.getPayMode()) && !production.scheduleCoverageComplete()) {
            throw ApiException.conflict("PAYROLL_PRODUCTION_NORM_INCOMPLETE",
                    "Для оклада сначала заполни график на весь расчётный месяц");
        }
        if ("SALARY".equals(term.getPayMode()) && production.productionNormMinutes() <= 0) {
            throw ApiException.conflict("PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "Для оклада нужна положительная расчётная норма месяца");
        }

        PayrollSourceSnapshot source = timeCompensation.payrollSource(user, month.atDay(1), month.atEndOfMonth());
        List<PayrollAdjustment> monthAdjustments = adjustments.findByOwnerAndPeriodMonthOrderByIdAsc(user, month.atDay(1));

        SettlementPreview settlementPreview =
                settlementPricing.preview(
                        user,
                        month,
                        term.getCurrencyCode()
                );

        if (!settlementPreview.ready()) {
            throw ApiException.conflict(
                    settlementPreview.blockingReason(),
                    settlementPreview.blockingMessage() == null
                            ? "Расчёт settlement для Payroll недоступен"
                            : settlementPreview.blockingMessage()
            );
        }

        OrdinaryPremiumPreview ordinaryPremiumPreview =
                ordinaryPremiumPricing.preview(
                        user,
                        month,
                        term.getCurrencyCode()
                );

        if (!ordinaryPremiumPreview.ready()) {
            throw ApiException.conflict(
                    ordinaryPremiumPreview.blockingReason(),
                    ordinaryPremiumPreview.blockingMessage() == null
                            ? "Расчёт обычных NIGHT / HOLIDAY доплат для Payroll недоступен"
                            : ordinaryPremiumPreview.blockingMessage()
            );
        }

        PayrollPreviewDto preview = preview(
                user,
                month,
                ensureSettings(user),
                term,
                production,
                source,
                monthAdjustments,
                settlementPreview,
                ordinaryPremiumPreview
        );

        if (!preview.compensationComponentCalculationReady()) {
            throw ApiException.conflict(
                    preview.compensationComponentCalculationBlockingReason(),
                    "Расчёт generic compensation components для Payroll недоступен"
            );
        }

        PayrollSnapshot previous = snapshots.findFirstByOwnerAndPeriodMonthOrderByRevisionDesc(user, month.atDay(1)).orElse(null);
        int revision = previous == null ? 1 : previous.getRevision() + 1;
        Instant checkedAt = Instant.now();
        String hash =
                calculationHash(
                        preview,
                        period.getClosedAt(),
                        monthAdjustments,
                        ordinaryPremiumPreview.pricingFingerprint()
                );

        PayrollSnapshot created = snapshots.saveAndFlush(new PayrollSnapshot(
                user, month.atDay(1), revision, preview.currencyCode(), preview.effectiveHourlyRateMinor(),
                preview.payMode(), YearMonth.parse(preview.compensationEffectiveMonth()).atDay(1),
                preview.configuredHourlyRateMinor(), preview.monthlySalaryMinor(),
                preview.productionNormMinutes(), preview.salaryCoveredMinutes(),
                preview.plannedMinutes(), preview.workedMinutes(), preview.vacationMinutes(), preview.sickMinutes(),
                preview.overtimeCompensatedMinutes(), preview.unpaidMinutes(), preview.timeAdjustmentMinutes(),
                preview.paidAbsenceMinutes(), preview.payableMinutes(),
                preview.hourlyBasePayableMinutes(), preview.basePayMinor(),
                preview.compensationComponentCount(),
                preview.compensationComponentEarningsMinor(),
                preview.compensationComponentFingerprint(),
                preview.ordinaryPremiumMinutes(),
                preview.ordinaryPremiumReferenceBasePayMinor(),
                preview.ordinaryPremiumPayMinor(),
                ordinaryPremiumPreview.pricingFingerprint(),
                preview.settlementCount(), preview.settlementMinutes(),
                preview.settlementBasePayMinor(), preview.settlementPremiumPayMinor(),
                preview.settlementPayMinor(), preview.settlementPricingFingerprint(),
                preview.additionsMinor(), preview.deductionsMinor(), preview.totalPayMinor(),
                period.getClosedAt(), checkedAt, hash));

        List<PayrollSnapshotComponentLine> frozenComponentLines =
                freezeComponentLines(
                        created,
                        preview.compensationComponentLines()
                );

        freezeSemanticEarnings(
                created,
                preview,
                source,
                ordinaryPremiumPreview,
                frozenComponentLines
        );

        if (previous != null) { previous.supersedeWith(created); snapshots.save(previous); }
        return toSnapshot(created);
    }

    private void freezeSemanticEarnings(
            PayrollSnapshot snapshot,
            PayrollPreviewDto preview,
            PayrollSourceSnapshot source,
            PayrollOrdinaryPremiumPreviewService.OrdinaryPremiumPreview
                    ordinaryPremiumPreview,
            List<PayrollSnapshotComponentLine> frozenComponentLines
    ) {
        /*
         * Compatibility path for historical direct-construction unit fixtures.
         *
         * In the real Spring application this collaborator is required by
         * @Autowired and therefore cannot silently disappear.
         */
        if (semanticEarningFreeze == null) {
            return;
        }

        if (ordinaryPremiumPreview == null) {
            throw new IllegalStateException(
                    "Semantic earning freeze requires ordinary premium preview"
            );
        }

        if (preview.ordinaryPremiumPayMinor()
                != ordinaryPremiumPreview.premiumAmountMinor()) {
            throw new IllegalStateException(
                    "Payroll ordinary premium aggregate differs from semantic preview"
            );
        }

        List<PayrollSemanticFreezeProjection.SemanticLine>
                semanticBasePayLines =
                        basePaySemanticProvenance == null
                                ? null
                                : basePaySemanticProvenance.lines(
                                        source,
                                        preview.payMode(),
                                        preview.configuredHourlyRateMinor(),
                                        preview.monthlySalaryMinor(),
                                        preview.productionNormMinutes(),
                                        preview.salaryCoveredMinutes(),
                                        preview.basePayMinor()
                                );

        List<PayrollSemanticFreezeProjection.ComponentLine>
                semanticComponentLines;

        if (componentSemanticProvenance != null) {
            YearMonth snapshotMonth =
                    YearMonth.from(
                            snapshot.getPeriodMonth()
                    );

            List<PayrollCombinationEpisodeFactService.EpisodeFact>
                    combinationFacts =
                    combinationEpisodeFacts == null
                            ? null
                            : combinationEpisodeFacts.resolveMonth(
                                    snapshot.getOwner(),
                                    snapshotMonth
                            );

            List<PayrollRegionalCoefficientSourceFactService.SourceFact>
                    regionalFacts =
                    regionalCoefficientSourceFacts == null
                            ? null
                            : regionalCoefficientSourceFacts.resolveMonth(
                                    snapshot.getOwner(),
                                    snapshotMonth
                            );

            semanticComponentLines =
                    componentSemanticProvenance.lines(
                            frozenComponentLines,
                            semanticBasePayLines,
                            combinationFacts,
                            regionalFacts,
                            snapshot.getCurrencyCode()
                    );
        } else {
            /*
             * Compatibility path for historical direct-construction tests:
             * preserve the pre-B3A aggregate component semantic freeze.
             */
            semanticComponentLines = null;

            if (frozenComponentLines != null) {
                java.util.ArrayList<
                        PayrollSemanticFreezeProjection.ComponentLine
                        > mapped =
                        new java.util.ArrayList<>();

                for (int index = 0;
                        index < frozenComponentLines.size();
                        index++) {

                    PayrollSnapshotComponentLine line =
                            frozenComponentLines.get(index);

                    if (line == null
                            || line.getLineIndex() != index) {
                        throw new IllegalStateException(
                                "Frozen compensation component semantic order is invalid"
                        );
                    }

                    mapped.add(
                            new PayrollSemanticFreezeProjection.ComponentLine(
                                    index,
                                    line.getEarningKind(),
                                    line.getAmountMinor()
                            )
                    );
                }

                semanticComponentLines =
                        List.copyOf(mapped);
            }
        }

        List<PayrollSemanticFreezeProjection.SemanticLine>
                semanticNightLines =
                        PayrollOrdinaryPremiumSemanticProvenance
                                .nightLines(
                                        ordinaryPremiumPreview
                                );

        semanticEarningFreeze.freeze(
                snapshot,
                new PayrollSemanticFreezeProjection.Source(
                        preview.basePayMinor(),
                        preview.ordinaryPremiumPayMinor(),
                        ordinaryPremiumPreview.nightPremiumAmountMinor(),
                        preview.settlementPayMinor(),
                        preview.compensationComponentEarningsMinor(),
                        semanticComponentLines,
                        preview.additionsMinor(),
                        semanticBasePayLines,
                        semanticNightLines
                )
        );
    }

    private PayrollPeriodDto buildPeriod(AppUser user, YearMonth month, PayrollSettings legacySettings) {
        LocalDate first = month.atDay(1); LocalDate last = month.atEndOfMonth();
        TimeAccountingPeriod accountingPeriod = accountingPeriods.findByOwnerAndPeriodMonth(user, first).orElse(null);
        boolean closed = accountingPeriod != null && accountingPeriod.isClosed();
        LedgerIntegrityDto integrity = ledgerIntegrity.inspect(user, first, last);
        PayrollSourceSnapshot source = timeCompensation.payrollSource(user, first, last);
        ProductionCalendarMonthDto production = productionCalendar.month(user, month.toString());
        CompensationTerm term = effectiveTerm(user, month);
        List<PayrollAdjustment> monthAdjustments = adjustments.findByOwnerAndPeriodMonthOrderByIdAsc(user, first);

        String payrollCurrency =
                term == null
                        ? legacySettings.getCurrencyCode()
                        : term.getCurrencyCode();

        SettlementPreview settlementPreview =
                settlementPricing.preview(
                        user,
                        month,
                        payrollCurrency
                );

        OrdinaryPremiumPreview ordinaryPremiumPreview =
                ordinaryPremiumPricing.preview(
                        user,
                        month,
                        payrollCurrency
                );

        PayrollPreviewDto preview = preview(
                user,
                month,
                legacySettings,
                term,
                production,
                source,
                monthAdjustments,
                settlementPreview,
                ordinaryPremiumPreview
        );

        List<PayrollSnapshotDto> history = snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(user, first).stream().map(this::toSnapshot).toList();
        List<PayrollCompensationTermDto> termHistory = compensationTerms.findByOwnerOrderByEffectiveFromDesc(user).stream().map(this::toTerm).toList();

        boolean compensationReady = term != null;
        boolean salaryMode = term != null && "SALARY".equals(term.getPayMode());
        boolean salaryCoverageReady = !salaryMode || production.scheduleCoverageComplete();
        boolean salaryNormReady = !salaryMode || production.productionNormMinutes() > 0;
        boolean componentPricingReady =
                preview.compensationComponentCalculationReady();
        boolean settlementPricingReady = settlementPreview.ready();
        boolean ordinaryPremiumPricingReady =
                ordinaryPremiumPreview.ready();

        boolean formulaReady =
                compensationReady
                        && salaryCoverageReady
                        && salaryNormReady
                        && componentPricingReady
                        && settlementPricingReady
                        && ordinaryPremiumPricingReady;

        boolean canCalculate =
                closed
                        && integrity.healthy()
                        && formulaReady;

        String blockingReason = !closed ? "PERIOD_OPEN"
                : !integrity.healthy() ? "LEDGER_INTEGRITY_FAILED"
                : !compensationReady ? "PAYROLL_COMPENSATION_REQUIRED"
                : !salaryCoverageReady ? "PAYROLL_PRODUCTION_NORM_INCOMPLETE"
                : !salaryNormReady ? "PAYROLL_PRODUCTION_NORM_REQUIRED"
                : !componentPricingReady
                    ? preview.compensationComponentCalculationBlockingReason()
                : !settlementPricingReady ? settlementPreview.blockingReason()
                : !ordinaryPremiumPricingReady ? ordinaryPremiumPreview.blockingReason()
                : null;
        return new PayrollPeriodDto(month.toString(), closed, integrity.healthy(), canCalculate, blockingReason,
                toSettings(legacySettings), production, preview,
                monthAdjustments.stream().map(this::toAdjustment).toList(),
                history.isEmpty() ? null : history.get(0), history,
                term == null ? null : toTerm(term), termHistory);
    }

    private PayrollPreviewDto preview(
            AppUser user,
            YearMonth month,
            PayrollSettings legacySettings,
            CompensationTerm term,
            ProductionCalendarMonthDto production,
            PayrollSourceSnapshot source,
            List<PayrollAdjustment> monthAdjustments,
            SettlementPreview settlementPreview,
            OrdinaryPremiumPreview ordinaryPremiumPreview
    ) {
        long additions =
                monthAdjustments.stream()
                        .filter(item ->
                                "ADDITION".equals(
                                        item.getAdjustmentType()
                                )
                        )
                        .mapToLong(
                                PayrollAdjustment::getAmountMinor
                        )
                        .sum();

        long deductions =
                monthAdjustments.stream()
                        .filter(item ->
                                "DEDUCTION".equals(
                                        item.getAdjustmentType()
                                )
                        )
                        .mapToLong(
                                PayrollAdjustment::getAmountMinor
                        )
                        .sum();

        String currency =
                term == null
                        ? legacySettings.getCurrencyCode()
                        : term.getCurrencyCode();

        String mode =
                term == null
                        ? null
                        : term.getPayMode();

        String effectiveMonth =
                term == null
                        ? null
                        : YearMonth.from(
                                term.getEffectiveFrom()
                        ).toString();

        Long configuredHourly =
                term == null
                        ? null
                        : term.getHourlyRateMinor();

        Long salary =
                term == null
                        ? null
                        : term.getMonthlySalaryMinor();

        long effectiveHourly = 0L;
        int salaryCovered = 0;
        long basePay = 0L;

        /*
         * EARNED_BASE_PAY is intentionally calculated before generic
         * components, NIGHT/HOLIDAY premiums and settlements.
         *
         * This gives generic percentage components a stable, acyclic base.
         */
        boolean previewFormulaReady =
                term != null
                        && (!"SALARY".equals(
                                term.getPayMode()
                        )
                        || (production.scheduleCoverageComplete()
                        && production.productionNormMinutes() > 0));

        if (previewFormulaReady) {
            Result result =
                    calculation.calculate(
                            term,
                            source,
                            production.productionNormMinutes()
                    );

            effectiveHourly =
                    result.effectiveHourlyRateMinor();

            salaryCovered =
                    result.salaryCoveredMinutes();

            basePay =
                    result.basePayMinor();
        }

        String componentUnavailableReason =
                term == null
                        ? "PAYROLL_COMPENSATION_REQUIRED"
                        : "SALARY".equals(term.getPayMode())
                        && !production.scheduleCoverageComplete()
                        ? "PAYROLL_PRODUCTION_NORM_INCOMPLETE"
                        : "SALARY".equals(term.getPayMode())
                        && production.productionNormMinutes() <= 0
                        ? "PAYROLL_PRODUCTION_NORM_REQUIRED"
                        : null;

        Context componentContext =
                previewFormulaReady
                        ? new Context(
                                currency,
                                mode,
                                salary,
                                basePay
                        )
                        : null;

        /*
         * B3D1 monthly/regional eligible-base authority.
         *
         * Only semantic amounts already proven before generic component
         * calculation may enter this seed pool. Generic component amounts are
         * appended by CompensationComponentCalculationService itself, while
         * deferred LOCAL_ELIGIBLE_EARNINGS targets are evaluated by semantic
         * phase: MONTHLY_BONUS before REGIONAL_COEFFICIENT.
         *
         * Any ordinary premium money that is still semantically unclassified
         * makes the local base incomplete. We do not guess that it is (or is
         * not) HOLIDAY_PAY.
         */
        List<PayrollEligibleEarningsBaseResolver.Earning>
                componentUpstreamSemanticEarnings =
                new java.util.ArrayList<>();

        if (previewFormulaReady) {
            componentUpstreamSemanticEarnings.add(
                    new PayrollEligibleEarningsBaseResolver.Earning(
                            ru.daniil.shifts.model.PayrollEarningKind.BASE_PAY,
                            basePay
                    )
            );

            if (ordinaryPremiumPreview.ready()
                    && ordinaryPremiumPreview.nightPremiumAmountMinor() > 0L) {
                componentUpstreamSemanticEarnings.add(
                        new PayrollEligibleEarningsBaseResolver.Earning(
                                ru.daniil.shifts.model.PayrollEarningKind.NIGHT_PREMIUM,
                                ordinaryPremiumPreview.nightPremiumAmountMinor()
                        )
                );
            }
        }

        boolean componentUpstreamSemanticEarningsComplete =
                previewFormulaReady
                        && ordinaryPremiumPreview.ready()
                        && ordinaryPremiumPreview
                        .unclassifiedPremiumAmountMinor() == 0L;

        ComponentPreview componentPreview =
                componentPreview(
                        user,
                        month,
                        componentContext,
                        componentUnavailableReason,
                        componentUpstreamSemanticEarnings,
                        componentUpstreamSemanticEarningsComplete
                );

        Projection componentProjection =
                componentPreview.ready()
                        ? componentPreview.projection()
                        : emptyComponentProjection();

        List<PayrollCompensationComponentLineDto> componentLines =
                componentProjection.lines()
                        .stream()
                        .map(this::toComponentLine)
                        .toList();

        long componentEarnings =
                componentProjection.totalAmountMinor();

        long settlementBasePay =
                settlementPreview.ready()
                        ? settlementPreview.baseAmountMinor()
                        : 0L;

        long settlementPremiumPay =
                settlementPreview.ready()
                        ? settlementPreview.premiumAmountMinor()
                        : 0L;

        long settlementPay =
                settlementPreview.ready()
                        ? settlementPreview.totalAmountMinor()
                        : 0L;

        long ordinaryPremiumReferenceBase =
                ordinaryPremiumPreview.ready()
                        ? ordinaryPremiumPreview.referenceBaseAmountMinor()
                        : 0L;

        /*
         * Delta-only invariant:
         * ordinary base is already represented by basePay.
         */
        long ordinaryPremiumPay =
                ordinaryPremiumPreview.ready()
                        ? ordinaryPremiumPreview.premiumAmountMinor()
                        : 0L;

        /*
         * Ordered earnings assembly over already-calculated money.
         *
         * This is intentionally only an assembly contract:
         * - phase ordering does not define eligible calculation bases;
         * - no PayrollEarningKind is inferred from displayName;
         * - settlement and generic components remain OTHER_EARNING until
         *   they expose an explicit machine-owned semantic identity.
         *
         * ordinaryPremiumPay is the delta-only NIGHT / HOLIDAY premium,
         * so its assembly phase is TIME_PREMIUM. Existing money sources and
         * snapshot fields remain unchanged.
         */
        long earningsSubtotal =
                assembleEarnings(
                        List.of(
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.BASE_PAY,
                                        basePay
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.TIME_PREMIUM,
                                        ordinaryPremiumPay
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        settlementPay
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        componentEarnings
                                )
                        )
                ).totalAmountMinor();

        long totalPay =
                safeMoney(
                        earningsSubtotal,
                        0L,
                        0L,
                        additions,
                        deductions
                );

        return new PayrollPreviewDto(
                month.toString(),
                currency,
                effectiveHourly,
                source.plannedMinutes(),
                source.workedMinutes(),
                source.vacationMinutes(),
                source.sickMinutes(),
                source.overtimeCompensatedMinutes(),
                source.unpaidMinutes(),
                source.timeAdjustmentMinutes(),
                source.paidAbsenceMinutes(),
                source.payableMinutes(),
                source.hourlyBasePayableMinutes(),
                basePay,
                componentPreview.ready(),
                componentPreview.blockingReason(),
                componentLines.size(),
                componentEarnings,
                componentProjection.fingerprint(),
                componentLines,
                ordinaryPremiumPreview.ready(),
                ordinaryPremiumPreview.blockingReason(),
                ordinaryPremiumPreview.pricingIdentityRequired(),
                ordinaryPremiumPreview.ordinaryMinutes(),
                ordinaryPremiumReferenceBase,
                ordinaryPremiumPay,
                settlementPreview.ready(),
                settlementPreview.blockingReason(),
                settlementPreview.pricingFingerprint(),
                settlementPreview.settlementCount(),
                settlementPreview.minutes(),
                settlementBasePay,
                settlementPremiumPay,
                settlementPay,
                additions,
                deductions,
                totalPay,
                mode,
                effectiveMonth,
                configuredHourly,
                salary,
                effectiveHourly,
                production.productionNormMinutes(),
                salaryCovered
        );
    }


    private ComponentPreview componentPreview(
            AppUser user,
            YearMonth month,
            Context context,
            String unavailableReason,
            List<PayrollEligibleEarningsBaseResolver.Earning>
                    upstreamSemanticEarnings,
            boolean upstreamSemanticEarningsComplete
    ) {
        /*
         * Compatibility path for isolated historical unit fixtures that
         * manually construct PayrollService. Real Spring runtime always
         * injects componentPricing.
         */
        if (componentPricing == null) {
            return new ComponentPreview(
                    month,
                    true,
                    null,
                    null,
                    emptyComponentProjection()
            );
        }

        return componentPricing.preview(
                user,
                month,
                context,
                unavailableReason,
                upstreamSemanticEarnings,
                upstreamSemanticEarningsComplete
        );
    }

    private Projection emptyComponentProjection() {
        return new Projection(
                0L,
                List.of(),
                null
        );
    }

    private PayrollCompensationComponentLineDto toComponentLine(
            CalculatedLine line
    ) {
        return new PayrollCompensationComponentLineDto(
                line.componentId(),
                line.versionId(),
                YearMonth.from(
                        line.effectiveFrom()
                ).toString(),
                line.displayName(),
                line.earningKind() == null
                        ? null
                        : line.earningKind().name(),
                line.calculationType().name(),
                line.calculationBase() == null
                        ? null
                        : line.calculationBase().name(),
                line.rateBps(),
                line.configuredAmountMinor(),
                line.configuredCurrencyCode(),
                line.referenceBaseMinor(),
                line.amountMinor()
        );
    }

    private List<PayrollCompensationComponentLineDto> frozenComponentLines(
            PayrollSnapshot snapshot
    ) {
        if (snapshotComponentLines == null) {
            /*
             * Compatibility path for historical isolated unit fixtures that
             * manually construct PayrollService.
             *
             * Real Spring runtime always injects the repository.
             */
            return List.of();
        }

        List<PayrollSnapshotComponentLine> frozen =
                snapshotComponentLines
                        .findBySnapshotOrderByLineIndexAsc(
                                snapshot
                        );

        validateFrozenComponentLines(
                snapshot,
                frozen
        );

        return frozen.stream()
                .map(line ->
                        new PayrollCompensationComponentLineDto(
                                line.getComponentId(),
                                line.getVersionId(),
                                YearMonth.from(
                                        line.getEffectiveFrom()
                                ).toString(),
                                line.getDisplayName(),
                                line.getEarningKind() == null
                                        ? null
                                        : line.getEarningKind().name(),
                                line.getCalculationType(),
                                line.getCalculationBase(),
                                line.getRateBps(),
                                line.getConfiguredAmountMinor(),
                                line.getConfiguredCurrencyCode(),
                                line.getReferenceBaseMinor(),
                                line.getAmountMinor()
                        )
                )
                .toList();
    }



    private void validateFrozenComponentLines(
            PayrollSnapshot snapshot,
            List<PayrollSnapshotComponentLine> lines
    ) {
        if (snapshot == null
                || lines == null) {
            throw new IllegalStateException(
                    "Frozen compensation component snapshot provenance is missing"
            );
        }

        if (lines.size()
                != snapshot.getCompensationComponentCount()) {
            throw new IllegalStateException(
                    "Frozen compensation component line count "
                            + "does not match snapshot aggregate"
            );
        }

        long amountSum = 0L;

        for (int i = 0; i < lines.size(); i++) {
            PayrollSnapshotComponentLine line =
                    lines.get(i);

            if (line == null
                    || line.getLineIndex() != i) {
                throw new IllegalStateException(
                        "Frozen compensation component line order "
                                + "does not match snapshot aggregate"
                );
            }

            try {
                amountSum =
                        Math.addExact(
                                amountSum,
                                line.getAmountMinor()
                        );

            } catch (ArithmeticException ex) {
                throw new IllegalStateException(
                        "Frozen compensation component line amount overflow",
                        ex
                );
            }
        }

        if (amountSum
                != snapshot.getCompensationComponentEarningsMinor()) {
            throw new IllegalStateException(
                    "Frozen compensation component earnings "
                            + "do not match snapshot aggregate"
            );
        }
    }

    private ru.daniil.shifts.model.PayrollEarningKind
            componentEarningKind(
                    String raw
            ) {
        if (raw == null) {
            return null;
        }

        final ru.daniil.shifts.model.PayrollEarningKind earningKind;

        try {
            earningKind =
                    ru.daniil.shifts.model.PayrollEarningKind
                            .valueOf(
                                    raw
                            );

        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Frozen compensation component earning kind is invalid",
                    ex
            );
        }

        if (!earningKind
                .isGenericCompensationComponentKind()) {
            throw new IllegalStateException(
                    "Frozen compensation component earning kind is unsupported"
            );
        }

        return earningKind;
    }

    private List<PayrollSnapshotComponentLine> freezeComponentLines(
            PayrollSnapshot snapshot,
            List<PayrollCompensationComponentLineDto> lines
    ) {
        /*
         * NULL means the historical direct-construction compatibility path
         * has no exact frozen component evidence available.
         *
         * An exact empty List means production proved there were no
         * compensation component lines.
         */
        if (snapshotComponentLines == null) {
            return null;
        }

        if (lines == null
                || lines.isEmpty()) {
            return List.of();
        }

        List<PayrollSnapshotComponentLine> frozen =
                new java.util.ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            PayrollCompensationComponentLineDto line =
                    lines.get(i);

            frozen.add(
                    new PayrollSnapshotComponentLine(
                            snapshot,
                            i,
                            line.componentId(),
                            line.versionId(),
                            YearMonth.parse(
                                    line.effectiveMonth()
                            ).atDay(1),
                            line.displayName(),
                            componentEarningKind(
                                    line.earningKind()
                            ),
                            line.calculationType(),
                            line.calculationBase(),
                            line.rateBps(),
                            line.configuredAmountMinor(),
                            line.configuredCurrencyCode(),
                            line.referenceBaseMinor(),
                            line.amountMinor()
                    )
            );
        }

        snapshotComponentLines.saveAllAndFlush(
                frozen
        );

        return List.copyOf(
                frozen
        );
    }

    private CompensationTerm effectiveTerm(AppUser user, YearMonth month) {
        return compensationTerms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, month.atDay(1)).orElse(null);
    }

    private LocalDate currentCompensationMonth(AppUser user) {
        ZoneId zone = ZoneId.of("UTC");
        String zoneText = user == null ? null : user.getWorkTimezone();
        if (zoneText != null && !zoneText.isBlank()) {
            try {
                zone = ZoneId.of(zoneText);
            } catch (DateTimeException ignored) {
                // AppUser normally stores a validated timezone; UTC is the safe compatibility fallback.
            }
        }
        return YearMonth.now(zone).atDay(1);
    }

    private CompensationTerm upsertTerm(AppUser user, LocalDate effectiveFrom, String mode, String currency,
                                        Long hourly, Long salary) {
        CompensationTerm term = compensationTerms.findByOwnerAndEffectiveFrom(user, effectiveFrom.withDayOfMonth(1))
                .orElseGet(() -> new CompensationTerm(user, effectiveFrom));
        term.update(mode, currency, hourly, salary);
        return compensationTerms.saveAndFlush(term);
    }

    private void validateTermShape(String mode, Long hourly, Long salary) {
        if ("HOURLY".equals(mode)) {
            if (hourly == null || hourly < 1 || hourly > 1_000_000_000L || salary != null)
                throw ApiException.badRequest("PAYROLL_COMPENSATION_INVALID", "Для почасовой оплаты укажи только положительную ставку");
        } else {
            if (salary == null || salary < 1 || salary > 1_000_000_000_000L || hourly != null)
                throw ApiException.badRequest("PAYROLL_COMPENSATION_INVALID", "Для оклада укажи только положительный месячный оклад");
        }
    }

    private TimeAccountingPeriod requireClosedPeriod(AppUser user, YearMonth month, boolean lock) {
        TimeAccountingPeriod period = (lock ? accountingPeriods.findForUpdateByOwnerAndPeriodMonth(user, month.atDay(1))
                : accountingPeriods.findByOwnerAndPeriodMonth(user, month.atDay(1)))
                .orElseThrow(() -> ApiException.conflict("PERIOD_NOT_CLOSED", "Сначала закрой расчётный период " + month));
        if (!period.isClosed()) throw ApiException.conflict("PERIOD_NOT_CLOSED", "Сначала закрой расчётный период " + month);
        return period;
    }

    private PayrollSettings ensureSettings(AppUser user) {
        return settings.findByOwner(user).orElseGet(() -> settings.saveAndFlush(new PayrollSettings(user)));
    }

    private PayrollEarningsPipeline.Result assembleEarnings(
            List<PayrollEarningsPipeline.Earning> earnings
    ) {
        try {
            return PayrollEarningsPipeline.assemble(
                    earnings
            );
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest(
                    "PAYROLL_AMOUNT_OVERFLOW",
                    "Итоговая сумма слишком велика"
            );
        }
    }

    private long safeMoney(
            long basePay,
            long settlementPay,
            long ordinaryPremiumPay,
            long additions,
            long deductions
    ) {
        try {
            return Math.subtractExact(
                    Math.addExact(
                            Math.addExact(
                                    Math.addExact(
                                            basePay,
                                            settlementPay
                                    ),
                                    ordinaryPremiumPay
                            ),
                            additions
                    ),
                    deductions
            );
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest(
                    "PAYROLL_AMOUNT_OVERFLOW",
                    "Итоговая сумма слишком велика"
            );
        }
    }

    private String calculationHash(
            PayrollPreviewDto preview,
            Instant closedAt,
            List<PayrollAdjustment> monthAdjustments,
            String ordinaryPremiumPricingFingerprint
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(preview.month()).append('|').append(preview.currencyCode()).append('|')
                .append(preview.payMode()).append('|').append(preview.compensationEffectiveMonth()).append('|')
                .append(preview.configuredHourlyRateMinor()).append('|').append(preview.monthlySalaryMinor()).append('|')
                .append(preview.effectiveHourlyRateMinor()).append('|').append(preview.productionNormMinutes()).append('|')
                .append(preview.salaryCoveredMinutes()).append('|').append(preview.plannedMinutes()).append('|')
                .append(preview.workedMinutes()).append('|').append(preview.vacationMinutes()).append('|')
                .append(preview.sickMinutes()).append('|').append(preview.overtimeCompensatedMinutes()).append('|')
                .append(preview.unpaidMinutes()).append('|').append(preview.timeAdjustmentMinutes()).append('|')
                .append(preview.paidAbsenceMinutes()).append('|').append(preview.payableMinutes()).append('|')
                .append(preview.hourlyBasePayableMinutes()).append('|')
                .append(preview.basePayMinor()).append('|')
                .append(preview.compensationComponentCount()).append('|')
                .append(preview.compensationComponentEarningsMinor()).append('|')
                .append(preview.compensationComponentFingerprint() == null
                        ? ""
                        : preview.compensationComponentFingerprint()).append('|')
                .append(preview.ordinaryPremiumMinutes()).append('|')
                .append(preview.ordinaryPremiumReferenceBasePayMinor()).append('|')
                .append(preview.ordinaryPremiumPayMinor()).append('|')
                .append(ordinaryPremiumPricingFingerprint == null
                        ? ""
                        : ordinaryPremiumPricingFingerprint).append('|')
                .append(preview.settlementCount()).append('|').append(preview.settlementMinutes()).append('|')
                .append(preview.settlementBasePayMinor()).append('|')
                .append(preview.settlementPremiumPayMinor()).append('|')
                .append(preview.settlementPayMinor()).append('|')
                .append(preview.settlementPricingFingerprint() == null
                        ? ""
                        : preview.settlementPricingFingerprint()).append('|')
                .append(preview.additionsMinor()).append('|')
                .append(preview.deductionsMinor()).append('|').append(preview.totalPayMinor()).append('|')
                .append(closedAt == null ? "" : closedAt.toString());
        for (PayrollAdjustment item : monthAdjustments) canonical.append('|').append(item.getId()).append(':')
                .append(item.getAdjustmentType()).append(':').append(item.getAmountMinor()).append(':').append(item.getTitle());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private PayrollSettingsDto toSettings(PayrollSettings value) {
        return new PayrollSettingsDto(value.getCurrencyCode(), value.getHourlyRateMinor(), value.getUpdatedAt() == null ? null : value.getUpdatedAt().toString());
    }
    private PayrollCompensationTermDto toTerm(CompensationTerm value) {
        return new PayrollCompensationTermDto(value.getId(), YearMonth.from(value.getEffectiveFrom()).toString(), value.getPayMode(),
                value.getCurrencyCode(), value.getHourlyRateMinor(), value.getMonthlySalaryMinor(),
                value.getUpdatedAt() == null ? null : value.getUpdatedAt().toString());
    }
    private PayrollAdjustmentDto toAdjustment(PayrollAdjustment value) {
        return new PayrollAdjustmentDto(value.getId(), YearMonth.from(value.getPeriodMonth()).toString(), value.getAdjustmentType(),
                value.getAmountMinor(), value.getTitle(), value.getNote(), value.getCreatedAt() == null ? null : value.getCreatedAt().toString());
    }
    private PayrollSnapshotDto toSnapshot(
            PayrollSnapshot value
    ) {
        List<PayrollCompensationComponentLineDto> componentLines =
                frozenComponentLines(
                        value
                );

        return new PayrollSnapshotDto(
                value.getId(),
                YearMonth.from(
                        value.getPeriodMonth()
                ).toString(),
                value.getRevision(),
                value.getCurrencyCode(),
                value.getHourlyRateMinor(),
                value.getPlannedMinutes(),
                value.getWorkedMinutes(),
                value.getVacationMinutes(),
                value.getSickMinutes(),
                value.getOvertimeCompensatedMinutes(),
                value.getUnpaidMinutes(),
                value.getTimeAdjustmentMinutes(),
                value.getPaidAbsenceMinutes(),
                value.getPayableMinutes(),
                value.getHourlyBasePayableMinutes(),
                value.getBasePayMinor(),
                value.getCompensationComponentCount(),
                value.getCompensationComponentEarningsMinor(),
                value.getCompensationComponentFingerprint(),
                componentLines,
                value.getOrdinaryPremiumMinutes(),
                value.getOrdinaryPremiumReferenceBasePayMinor(),
                value.getOrdinaryPremiumPayMinor(),
                value.getOrdinaryPremiumPricingFingerprint(),
                value.getSettlementCount(),
                value.getSettlementMinutes(),
                value.getSettlementBasePayMinor(),
                value.getSettlementPremiumPayMinor(),
                value.getSettlementPayMinor(),
                value.getSettlementPricingFingerprint(),
                value.getAdditionsMinor(),
                value.getDeductionsMinor(),
                value.getTotalPayMinor(),
                value.getSourcePeriodClosedAt().toString(),
                value.getSourceIntegrityCheckedAt().toString(),
                value.getCalculationHash(),
                value.getCreatedAt().toString(),
                value.getSupersededBy() == null
                        ? null
                        : value.getSupersededBy().getId(),
                value.getPayMode(),
                YearMonth.from(
                        value.getCompensationEffectiveFrom()
                ).toString(),
                value.getConfiguredHourlyRateMinor(),
                value.getMonthlySalaryMinor(),
                value.getHourlyRateMinor(),
                value.getProductionNormMinutes(),
                value.getSalaryCoveredMinutes()
        );
    }

    private YearMonth parseMonth(String value) {
        try { return YearMonth.parse(value == null ? "" : value.trim()); }
        catch (DateTimeParseException ex) { throw ApiException.badRequest("PAYROLL_MONTH_INVALID", "Месяц должен быть в формате yyyy-MM"); }
    }
    private String normalizeCurrency(String value) {
        String currency = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw ApiException.badRequest("PAYROLL_CURRENCY_INVALID", "Код валюты должен состоять из трёх букв");
        return currency;
    }
    private String normalizePayMode(String value) {
        String mode = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"HOURLY".equals(mode) && !"SALARY".equals(mode)) throw ApiException.badRequest("PAYROLL_MODE_INVALID", "Способ оплаты должен быть HOURLY или SALARY");
        return mode;
    }
    private String normalizeAdjustmentType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"ADDITION".equals(type) && !"DEDUCTION".equals(type)) throw ApiException.badRequest("PAYROLL_ADJUSTMENT_TYPE_INVALID", "Тип должен быть ADDITION или DEDUCTION");
        return type;
    }
    private String cleanRequired(String value, int max, String message) {
        String clean = value == null ? "" : value.trim(); if (clean.isEmpty()) throw ApiException.badRequest(message);
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
    private String cleanOptional(String value, int max) {
        String clean = value == null ? "" : value.trim(); if (clean.isEmpty()) return null;
        return clean.length() > max ? clean.substring(0, max) : clean;
    }
}
