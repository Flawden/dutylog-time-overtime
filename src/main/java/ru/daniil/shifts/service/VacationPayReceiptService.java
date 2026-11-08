package ru.daniil.shifts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.VacationPayCalculationEvidenceDto;
import ru.daniil.shifts.dto.Dtos.VacationPayCalculationSnapshotDto;
import ru.daniil.shifts.dto.Dtos.VacationPayHolidayFactDto;
import ru.daniil.shifts.dto.Dtos.VacationPayReceiptDto;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.VacationPayCalculationSnapshot;
import ru.daniil.shifts.model.VacationPayReceipt;
import ru.daniil.shifts.repo.AbsencePeriodRepository;
import ru.daniil.shifts.repo.VacationPayCalculationSnapshotRepository;
import ru.daniil.shifts.repo.VacationPayReceiptRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Records immutable actual incoming vacation-pay transfers.
 * Calculation evidence is stored as a separate immutable snapshot for the absence,
 * so multiple transfers never duplicate or multiply the employer's calculated gross amount.
 */
@Service
public class VacationPayReceiptService {

    public static final String CALCULATION_SCHEMA_VERSION =
            "VACATION_PAY_RECEIPT_CALCULATION_V1";

    private final AbsencePeriodRepository absences;
    private final VacationPayReceiptRepository receipts;
    private final VacationPayCalculationSnapshotRepository snapshots;
    private final VacationPayableDaysFactService payableDays;
    private final VacationPayApplicationService vacationPay;
    private final ObjectMapper objectMapper;

    public VacationPayReceiptService(
            AbsencePeriodRepository absences,
            VacationPayReceiptRepository receipts,
            VacationPayCalculationSnapshotRepository snapshots,
            VacationPayableDaysFactService payableDays,
            VacationPayApplicationService vacationPay,
            ObjectMapper objectMapper
    ) {
        this.absences = Objects.requireNonNull(absences, "Absence repository is required");
        this.receipts = Objects.requireNonNull(receipts, "Vacation receipt repository is required");
        this.snapshots = Objects.requireNonNull(snapshots, "Calculation snapshot repository is required");
        this.payableDays = Objects.requireNonNull(payableDays, "Vacation payable-days authority is required");
        this.vacationPay = Objects.requireNonNull(
                vacationPay,
                "Canonical vacation-pay application service is required"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required");
    }

    @Transactional
    public VacationPayReceiptDto register(
            AppUser user,
            Long absencePeriodId,
            LocalDate receivedDate,
            long actualReceivedAmountMinor,
            String actualCurrencyCode,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Vacation receipt requires authenticated user");
        if (user.getId() == null) {
            throw ApiException.unauthorized(
                    "VACATION_PAY_RECEIPT_USER_REQUIRED",
                    "Для регистрации отпускных необходим сохранённый пользователь"
            );
        }
        if (receivedDate == null) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_DATE_INVALID",
                    "Дата фактического получения обязательна"
            );
        }
        if (absencePeriodId == null || absencePeriodId <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_ABSENCE_REQUIRED",
                    "Для регистрации отпускных необходимо выбрать отпуск"
            );
        }
        if (actualReceivedAmountMinor <= 0L) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_AMOUNT_INVALID",
                    "Фактически полученная сумма должна быть положительной"
            );
        }
        if (actualCurrencyCode == null || !actualCurrencyCode.matches("[A-Z]{3}")) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_CURRENCY_INVALID",
                    "Код валюты фактического поступления должен состоять из трёх заглавных букв"
            );
        }

        boolean calculationRequested = discoveryThroughMonth != null || provenNoPayrollMonths != null;
        if (calculationRequested && (discoveryThroughMonth == null || provenNoPayrollMonths == null)) {
            throw ApiException.badRequest(
                    "VACATION_PAY_RECEIPT_CALCULATION_INPUT_INCOMPLETE",
                    "Для дополнительного расчёта необходимо передать discoveryThroughMonth и provenNoPayrollMonths"
            );
        }

        AbsencePeriod absence = absences.findByIdAndOwner(absencePeriodId, user)
                .orElseThrow(() -> ApiException.notFound(
                        "Отпуск не найден или недоступен текущему пользователю"
                ));
        return registerForAbsence(
                user,
                absence,
                receivedDate,
                actualReceivedAmountMinor,
                actualCurrencyCode,
                discoveryThroughMonth,
                provenNoPayrollMonths
        );
    }

    @Transactional(readOnly = true)
    public VacationPayReceiptDto get(AppUser user, Long receiptId) {
        Objects.requireNonNull(user, "Vacation receipt lookup requires authenticated user");
        VacationPayReceipt receipt = receipts.findByIdAndOwner(receiptId, user)
                .orElseThrow(() -> ApiException.notFound("Поступление не найдено или недоступно текущему пользователю"));
        return toDto(receipt);
    }

    @Transactional(readOnly = true)
    public List<VacationPayReceiptDto> list(AppUser user) {
        Objects.requireNonNull(user, "Vacation receipt listing requires authenticated user");
        return receipts.findByOwnerOrderByReceivedDateDescIdDesc(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private VacationPayReceiptDto registerForAbsence(
            AppUser user,
            AbsencePeriod absence,
            LocalDate receivedDate,
            long actualReceivedAmountMinor,
            String actualCurrencyCode,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        if (absence.getStartDate() == null || absence.getEndDate() == null
                || absence.getEndDate().isBefore(absence.getStartDate())) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_ABSENCE_SPAN_INVALID",
                    "У выбранного отпуска некорректный период"
            );
        }

        Long absencePeriodId = absence.getId();
        LocalDate eventDate = absence.getStartDate();
        VacationPayableDaysFactService.Resolution vacationIdentity = Objects.requireNonNull(
                payableDays.resolve(user, eventDate, absencePeriodId),
                "Vacation payable-days identity authority returned null"
        );
        if (!vacationIdentity.ready()
                && !VacationPayableDaysFactService.HOLIDAY_AUTHORITY_UNAVAILABLE.equals(
                        vacationIdentity.blockingReason()
                )) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_VACATION_NOT_ELIGIBLE",
                    "Выбранное отсутствие не является подтверждённым отпуском, для которого можно зарегистрировать отпускные"
            );
        }

        AbsenceType type = absence.getType();
        if (type == null || !samePersistedOwner(user, type.getOwner())) {
            throw ApiException.conflict(
                    "VACATION_PAY_RECEIPT_ABSENCE_TYPE_OWNERSHIP_MISMATCH",
                    "Тип выбранного отпуска не принадлежит текущему пользователю"
            );
        }
        String absenceStatus = requireSnapshotText(absence.getStatus(), "Absence status is required");
        String absenceTypeSystemCode = requireSnapshotText(type.getSystemCode(), "Absence system code is required");
        String absenceTypeBalancePolicy = requireSnapshotText(type.getBalancePolicy(), "Absence balance policy is required");
        String absenceCoverage = requireSnapshotText(absence.getCoverage(), "Absence coverage is required");
        String absenceCompensationPolicy = requireSnapshotText(
                absence.getCompensationPolicy(),
                "Absence compensation policy is required"
        );

        VacationPayCalculationSnapshot snapshot;
        if (discoveryThroughMonth != null) {
            List<YearMonth> proofMonths = List.copyOf(provenNoPayrollMonths);
            snapshot = createCalculationSnapshot(
                    user,
                    absence,
                    absenceStatus,
                    absenceTypeSystemCode,
                    absenceTypeBalancePolicy,
                    absenceCoverage,
                    absenceCompensationPolicy,
                    eventDate,
                    discoveryThroughMonth,
                    proofMonths
            );
        } else {
            snapshot = snapshots.findFirstByOwnerAndAbsencePeriod_IdOrderByIdDesc(user, absencePeriodId)
                    .orElse(null);
            if (snapshot != null && (!Objects.equals(snapshot.getCalculationEventDate(), eventDate)
                    || !Objects.equals(snapshot.getVacationFrom(), absence.getStartDate())
                    || !Objects.equals(snapshot.getVacationTo(), absence.getEndDate())
                    || !Objects.equals(snapshot.getAbsenceStatus(), absenceStatus)
                    || !Objects.equals(snapshot.getAbsenceTypeSystemCode(), absenceTypeSystemCode)
                    || !Objects.equals(snapshot.getAbsenceTypeBalancePolicy(), absenceTypeBalancePolicy)
                    || !Objects.equals(snapshot.getAbsenceCoverage(), absenceCoverage)
                    || !Objects.equals(snapshot.getAbsenceCompensationPolicy(), absenceCompensationPolicy))) {
                // Actual money must still be recordable if the absence identity has since changed.
                // A stale calculation snapshot is simply not attached to this new transfer.
                snapshot = null;
            }
        }

        String fingerprint = receiptFingerprint(
                user.getId(),
                absencePeriodId,
                snapshot == null ? null : snapshot.getId(),
                receivedDate,
                actualReceivedAmountMinor,
                actualCurrencyCode,
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                eventDate,
                absence.getStartDate(),
                absence.getEndDate()
        );

        VacationPayReceipt receipt = new VacationPayReceipt(
                user,
                absence,
                snapshot,
                receivedDate,
                actualReceivedAmountMinor,
                actualCurrencyCode,
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                eventDate,
                absence.getStartDate(),
                absence.getEndDate(),
                fingerprint
        );

        return toDto(receipts.saveAndFlush(receipt));
    }

    private VacationPayCalculationSnapshot createCalculationSnapshot(
            AppUser user,
            AbsencePeriod absence,
            String absenceStatus,
            String absenceTypeSystemCode,
            String absenceTypeBalancePolicy,
            String absenceCoverage,
            String absenceCompensationPolicy,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> proofMonths
    ) {
        VacationPayApplicationService.Resolution calculation = Objects.requireNonNull(
                vacationPay.resolve(
                        user,
                        eventDate,
                        absence.getId(),
                        discoveryThroughMonth,
                        proofMonths
                ),
                "Canonical vacation-pay application returned null"
        );

        if (!eventDate.equals(calculation.eventDate())
                || !Objects.equals(absence.getId(), calculation.requestedAbsencePeriodId())) {
            throw new IllegalStateException("Vacation calculation returned a different absence identity");
        }

        String calculationStatus;
        Long calculatedGrossAmountMinor = null;
        Integer calculatedPayableCalendarDays = null;
        String calculationCurrencyCode = null;
        String calculationBlockingReason = null;
        VacationPayCalculationSnapshotDto snapshotDto;

        if (calculation.ready()) {
            VacationPayCalculationEvidenceDto evidence = evidence(calculation);
            snapshotDto = new VacationPayCalculationSnapshotDto(
                    CALCULATION_SCHEMA_VERSION,
                    VacationPayCalculationSnapshot.CALCULATION_READY,
                    eventDate.toString(),
                    discoveryThroughMonth.toString(),
                    proofMonths.stream().map(YearMonth::toString).toList(),
                    null,
                    null,
                    null,
                    evidence
            );
            calculatedGrossAmountMinor = evidence.calculatedGrossAmountMinor();
            calculatedPayableCalendarDays = evidence.payableCalendarDays();
            calculationCurrencyCode = evidence.currencyCode();
            calculationStatus = VacationPayCalculationSnapshot.CALCULATION_READY;
        } else {
            calculationBlockingReason = calculation.blockingReason() == null
                    ? "UNKNOWN"
                    : calculation.blockingReason();
            snapshotDto = new VacationPayCalculationSnapshotDto(
                    CALCULATION_SCHEMA_VERSION,
                    VacationPayCalculationSnapshot.CALCULATION_BLOCKED,
                    eventDate.toString(),
                    discoveryThroughMonth.toString(),
                    proofMonths.stream().map(YearMonth::toString).toList(),
                    calculation.blockingStage() == null ? "UNKNOWN" : calculation.blockingStage().name(),
                    calculationBlockingReason,
                    calculation.upstreamBlockingReason(),
                    null
            );
            calculationStatus = VacationPayCalculationSnapshot.CALCULATION_BLOCKED;
        }

        String payload = serialize(snapshotDto);
        String fingerprint = calculationSnapshotFingerprint(
                user.getId(),
                absence.getId(),
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                eventDate,
                absence.getStartDate(),
                absence.getEndDate(),
                calculationStatus,
                calculatedGrossAmountMinor,
                calculatedPayableCalendarDays,
                CALCULATION_SCHEMA_VERSION,
                calculationCurrencyCode,
                payload,
                calculationBlockingReason
        );

        return snapshots.saveAndFlush(new VacationPayCalculationSnapshot(
                user,
                absence,
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                eventDate,
                absence.getStartDate(),
                absence.getEndDate(),
                calculationStatus,
                calculatedGrossAmountMinor,
                calculatedPayableCalendarDays,
                CALCULATION_SCHEMA_VERSION,
                calculationCurrencyCode,
                payload,
                calculationBlockingReason,
                fingerprint
        ));
    }

    private VacationPayCalculationEvidenceDto evidence(
            VacationPayApplicationService.Resolution calculation
    ) {
        if (!calculation.ready()
                || calculation.selectedBasis() == null
                || calculation.vacationPay() == null
                || !calculation.vacationPay().ready()) {
            throw new IllegalStateException("Ready vacation calculation has incomplete application authority");
        }

        VacationPayOrchestrator.Resolution orchestration = calculation.vacationPay();
        VacationPayMoneyFormula.Resolution money = orchestration.moneyAuthority();
        if (money == null || !money.ready() || money.vacationPayMinor() == null
                || money.vacationPayMinor() < 0L || money.currencyCode() == null
                || !money.currencyCode().matches("[A-Z]{3}")) {
            throw new IllegalStateException("Ready vacation calculation has incomplete money authority");
        }

        VacationAverageUnifiedDailyResolver.Resolution daily = money.dailyAuthority();
        VacationPayableDaysFactService.Resolution payable = money.payableDaysAuthority();
        if (daily == null || !daily.ready() || payable == null || !payable.ready()) {
            throw new IllegalStateException("Ready vacation calculation has incomplete K/L authority");
        }

        VacationAveragePrimaryCalculationService.Resolution reference = daily.referenceCalculation();
        VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis paragraph7 =
                daily.paragraph7CalendarBasis();
        VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis paragraph8 =
                daily.paragraph8FormulaBasis();
        VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily = money.averageDaily();
        VacationPayMoneyFormula.ExactVacationPay exact = money.exactVacationPay();

        if (averageDaily == null || exact == null
                || payable.vacationFrom() == null || payable.vacationTo() == null
                || !Objects.equals(payable.absencePeriodId(), calculation.requestedAbsencePeriodId())
                || !calculation.eventDate().equals(payable.vacationFrom())
                || money.payableCalendarDays() != payable.payableCalendarDays()) {
            throw new IllegalStateException("Ready vacation calculation evidence is inconsistent");
        }

        List<VacationPayHolidayFactDto> holidayFacts = payable.holidayFacts().stream()
                .map(fact -> new VacationPayHolidayFactDto(
                        fact.date().toString(),
                        fact.nonWorkingHoliday(),
                        fact.authorityCode(),
                        fact.dayKind()
                ))
                .toList();

        return new VacationPayCalculationEvidenceDto(
                CALCULATION_SCHEMA_VERSION,
                VacationPayApplicationService.RULE_ID,
                VacationPayableDaysFactService.RULE_ID,
                AnnualPaidVacationHolidayPolicy.RULE_ID,
                VacationPayMoneyFormula.RULE_ID,
                money.roundingPolicy(),
                calculation.eventDate().toString(),
                calculation.eventMonth().toString(),
                calculation.requestedAbsencePeriodId(),
                payable.vacationFrom().toString(),
                payable.vacationTo().toString(),
                payable.status(),
                calculation.discoveryThroughMonth().toString(),
                calculation.provenNoPayrollMonths().stream().map(YearMonth::toString).toList(),
                calculation.selectedBasis().name(),
                money.currencyCode(),
                averageDaily.numeratorMinor().toString(),
                averageDaily.denominatorDays().toString(),
                money.payableCalendarDays(),
                exact.numeratorMinor().toString(),
                exact.denominator().toString(),
                money.vacationPayMinor(),
                reference == null ? null : reference.referenceFrom().toString(),
                reference == null ? null : reference.referenceTo().toString(),
                paragraph7 == null ? null : paragraph7.countableCalendarDays(),
                paragraph7 == null ? null : paragraph7.authorityCode(),
                paragraph8 == null ? null : paragraph8.establishedBasis().name(),
                paragraph8 == null ? null : paragraph8.policy().name(),
                paragraph8 == null ? null : paragraph8.authorityCode(),
                paragraph8 == null ? null : paragraph8.annualNormMinutes(),
                payable.physicalSpanDates().stream().map(LocalDate::toString).toList(),
                payable.payableDates().stream().map(LocalDate::toString).toList(),
                payable.excludedHolidayDates().stream().map(LocalDate::toString).toList(),
                holidayFacts
        );
    }


    private VacationPayReceiptDto toDto(VacationPayReceipt receipt) {
        VacationPayCalculationSnapshot calculation = receipt.getCalculationSnapshot();
        Long snapshotId = calculation == null ? null : calculation.getId();
        String expectedFingerprint = receiptFingerprint(
                receipt.getOwner().getId(),
                receipt.getAbsencePeriod().getId(),
                snapshotId,
                receipt.getReceivedDate(),
                receipt.getActualReceivedAmountMinor(),
                receipt.getCurrencyCode(),
                receipt.getAbsenceStatus(),
                receipt.getAbsenceTypeSystemCode(),
                receipt.getAbsenceTypeBalancePolicy(),
                receipt.getAbsenceCoverage(),
                receipt.getAbsenceCompensationPolicy(),
                receipt.getCalculationEventDate(),
                receipt.getVacationFrom(),
                receipt.getVacationTo()
        );
        if (!expectedFingerprint.equals(receipt.getReceiptFingerprint())) {
            throw new IllegalStateException("Vacation-pay receipt fingerprint mismatch");
        }

        VacationPayCalculationSnapshotDto snapshot = calculation == null
                ? null
                : toSnapshotDto(calculation);

        if (!Objects.equals(receipt.getCalculationEventDate(), receipt.getVacationFrom())
                || receipt.getVacationTo().isBefore(receipt.getVacationFrom())
                || !List.of("APPROVED", "COMPLETED").contains(receipt.getAbsenceStatus())
                || !"VACATION".equals(receipt.getAbsenceTypeSystemCode())
                || !"VACATION_DAYS".equals(receipt.getAbsenceTypeBalancePolicy())
                || !"FULL_DAY".equals(receipt.getAbsenceCoverage())
                || !"VACATION_ALLOWANCE".equals(receipt.getAbsenceCompensationPolicy())) {
            throw new IllegalStateException("Stored vacation receipt identity is inconsistent");
        }

        return new VacationPayReceiptDto(
                receipt.getId(),
                receipt.getAbsencePeriod().getId(),
                receipt.getCalculationEventDate().toString(),
                receipt.getVacationFrom().toString(),
                receipt.getVacationTo().toString(),
                receipt.getReceivedDate().toString(),
                receipt.getActualReceivedAmountMinor(),
                receipt.getCurrencyCode(),
                calculation == null ? null : calculation.getCalculationCurrencyCode(),
                calculation == null || calculation.getCalculationCurrencyCode() == null
                        ? null
                        : receipt.getCurrencyCode().equals(calculation.getCalculationCurrencyCode()),
                receipt.getAbsenceStatus(),
                receipt.getAbsenceTypeSystemCode(),
                receipt.getAbsenceTypeBalancePolicy(),
                receipt.getAbsenceCoverage(),
                receipt.getAbsenceCompensationPolicy(),
                calculation == null
                        ? VacationPayCalculationSnapshot.CALCULATION_NOT_REQUESTED
                        : calculation.getCalculationStatus(),
                calculation == null ? null : calculation.getCalculatedGrossAmountMinor(),
                calculation == null ? null : calculation.getPayableCalendarDays(),
                calculation == null ? null : calculation.getCalculationBlockingReason(),
                calculation == null ? null : calculation.getCalculationSchemaVersion(),
                receipt.getReceiptFingerprint(),
                receipt.getCreatedAt().toString(),
                snapshot
        );
    }

    private VacationPayCalculationSnapshotDto toSnapshotDto(VacationPayCalculationSnapshot stored) {
        String expectedFingerprint = calculationSnapshotFingerprint(
                stored.getOwner().getId(),
                stored.getAbsencePeriod().getId(),
                stored.getAbsenceStatus(),
                stored.getAbsenceTypeSystemCode(),
                stored.getAbsenceTypeBalancePolicy(),
                stored.getAbsenceCoverage(),
                stored.getAbsenceCompensationPolicy(),
                stored.getCalculationEventDate(),
                stored.getVacationFrom(),
                stored.getVacationTo(),
                stored.getCalculationStatus(),
                stored.getCalculatedGrossAmountMinor(),
                stored.getPayableCalendarDays(),
                stored.getCalculationSchemaVersion(),
                stored.getCalculationCurrencyCode(),
                stored.getCalculationPayloadJson(),
                stored.getCalculationBlockingReason()
        );
        if (!expectedFingerprint.equals(stored.getSnapshotFingerprint())) {
            throw new IllegalStateException("Vacation-pay calculation snapshot fingerprint mismatch");
        }

        VacationPayCalculationSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(
                    stored.getCalculationPayloadJson(),
                    VacationPayCalculationSnapshotDto.class
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored vacation-pay calculation snapshot cannot be read", ex);
        }

        if (!CALCULATION_SCHEMA_VERSION.equals(stored.getCalculationSchemaVersion())
                || !CALCULATION_SCHEMA_VERSION.equals(snapshot.schemaVersion())
                || !Objects.equals(stored.getCalculationEventDate().toString(), snapshot.eventDate())
                || !Objects.equals(stored.getCalculationStatus(), snapshot.status())
                || snapshot.discoveryThroughMonth() == null
                || snapshot.provenNoPayrollMonths() == null) {
            throw new IllegalStateException("Stored calculation snapshot contradicts its summary");
        }

        if (VacationPayCalculationSnapshot.CALCULATION_READY.equals(stored.getCalculationStatus())) {
            VacationPayCalculationEvidenceDto evidence = snapshot.evidence();
            if (evidence == null
                    || !CALCULATION_SCHEMA_VERSION.equals(evidence.schemaVersion())
                    || !VacationPayApplicationService.RULE_ID.equals(evidence.applicationRuleId())
                    || !VacationPayableDaysFactService.RULE_ID.equals(evidence.payableDaysRuleId())
                    || !AnnualPaidVacationHolidayPolicy.RULE_ID.equals(evidence.holidayPolicyRuleId())
                    || !VacationPayMoneyFormula.RULE_ID.equals(evidence.moneyFormulaRuleId())
                    || evidence.currencyCode() == null
                    || !evidence.currencyCode().matches("[A-Z]{3}")
                    || evidence.selectedBasis() == null
                    || !Objects.equals(stored.getCalculationCurrencyCode(), evidence.currencyCode())
                    || !Objects.equals(stored.getAbsenceStatus(), evidence.absenceStatus())
                    || snapshot.blockingStage() != null
                    || snapshot.blockingReason() != null
                    || snapshot.upstreamBlockingReason() != null
                    || !Objects.equals(snapshot.discoveryThroughMonth(), evidence.discoveryThroughMonth())
                    || !Objects.equals(snapshot.provenNoPayrollMonths(), evidence.provenNoPayrollMonths())
                    || stored.getCalculatedGrossAmountMinor() == null
                    || stored.getPayableCalendarDays() == null
                    || stored.getCalculatedGrossAmountMinor() != evidence.calculatedGrossAmountMinor()
                    || stored.getPayableCalendarDays() != evidence.payableCalendarDays()
                    || !Objects.equals(stored.getCalculationEventDate().toString(), evidence.eventDate())
                    || !Objects.equals(stored.getVacationFrom().toString(), evidence.vacationFrom())
                    || !Objects.equals(stored.getVacationTo().toString(), evidence.vacationTo())
                    || !Objects.equals(stored.getAbsencePeriod().getId(), evidence.absencePeriodId())) {
                throw new IllegalStateException("Stored ready calculation contradicts frozen evidence");
            }
        } else if (VacationPayCalculationSnapshot.CALCULATION_BLOCKED.equals(stored.getCalculationStatus())) {
            if (snapshot.evidence() != null
                    || stored.getCalculationCurrencyCode() != null
                    || stored.getCalculationBlockingReason() == null
                    || snapshot.blockingReason() == null
                    || !Objects.equals(stored.getCalculationBlockingReason(), snapshot.blockingReason())
                    || snapshot.blockingStage() == null) {
                throw new IllegalStateException("Stored blocked calculation contradicts frozen evidence");
            }
        } else {
            throw new IllegalStateException("Stored calculation snapshot has unsupported status");
        }
        return snapshot;
    }

    private static boolean samePersistedOwner(AppUser requested, AppUser actual) {
        return requested != null && requested.getId() != null
                && actual != null && actual.getId() != null
                && requested.getId().equals(actual.getId());
    }

    private static String requireSnapshotText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
        return value;
    }

    private String serialize(VacationPayCalculationSnapshotDto snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize immutable vacation-pay calculation snapshot", ex);
        }
    }

    private static String receiptFingerprint(
            Long ownerId,
            Long absencePeriodId,
            Long calculationSnapshotId,
            LocalDate receivedDate,
            long actualReceivedAmountMinor,
            String currencyCode,
            String absenceStatus,
            String absenceTypeSystemCode,
            String absenceTypeBalancePolicy,
            String absenceCoverage,
            String absenceCompensationPolicy,
            LocalDate calculationEventDate,
            LocalDate vacationFrom,
            LocalDate vacationTo
    ) {
        String canonical = String.join("\n",
                "DUTYLOG_VACATION_PAY_RECEIPT_V4",
                String.valueOf(ownerId),
                String.valueOf(absencePeriodId),
                String.valueOf(calculationSnapshotId),
                receivedDate.toString(),
                Long.toString(actualReceivedAmountMinor),
                currencyCode,
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                calculationEventDate.toString(),
                vacationFrom.toString(),
                vacationTo.toString()
        );
        return sha256(canonical);
    }

    private static String calculationSnapshotFingerprint(
            Long ownerId,
            Long absencePeriodId,
            String absenceStatus,
            String absenceTypeSystemCode,
            String absenceTypeBalancePolicy,
            String absenceCoverage,
            String absenceCompensationPolicy,
            LocalDate calculationEventDate,
            LocalDate vacationFrom,
            LocalDate vacationTo,
            String calculationStatus,
            Long calculatedGrossAmountMinor,
            Integer payableCalendarDays,
            String calculationSchemaVersion,
            String calculationCurrencyCode,
            String calculationPayload,
            String calculationBlockingReason
    ) {
        String canonical = String.join("\n",
                "DUTYLOG_VACATION_PAY_CALCULATION_SNAPSHOT_V1",
                String.valueOf(ownerId),
                String.valueOf(absencePeriodId),
                absenceStatus,
                absenceTypeSystemCode,
                absenceTypeBalancePolicy,
                absenceCoverage,
                absenceCompensationPolicy,
                calculationEventDate.toString(),
                vacationFrom.toString(),
                vacationTo.toString(),
                calculationStatus,
                String.valueOf(calculatedGrossAmountMinor),
                String.valueOf(payableCalendarDays),
                String.valueOf(calculationSchemaVersion),
                calculationCurrencyCode == null ? "" : calculationCurrencyCode,
                calculationPayload == null ? "" : calculationPayload,
                calculationBlockingReason == null ? "" : calculationBlockingReason
        );
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
