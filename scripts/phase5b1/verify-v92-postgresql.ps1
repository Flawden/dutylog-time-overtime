$ErrorActionPreference = "Stop"

$root = (& git rev-parse --show-toplevel).Trim()
Set-Location $root

$Migration = Join-Path $root "src/main/resources/db/migration/postgresql/V92__vacation_pay_receipts.sql"
if (!(Test-Path -LiteralPath $Migration)) {
    throw "V92 migration not found: $Migration"
}

if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI not found. Install/start Docker Desktop before running this isolated PostgreSQL test."
}

& $env:ComSpec /d /c "docker info >NUL 2>&1"
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not available. Start Docker Desktop and retry."
}

$Suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
$Container = "dutylog-phase5b1-pg-$Suffix"
$TempRoot = Join-Path $env:TEMP $Container
$BaselineFile = Join-Path $TempRoot "baseline.sql"
$SmokeFile = Join-Path $TempRoot "smoke.sql"

New-Item -ItemType Directory -Path $TempRoot -Force | Out-Null

$BaselineSql = @'
CREATE TABLE users (
    id BIGINT PRIMARY KEY
);

CREATE TABLE absence_periods (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id)
);

INSERT INTO users (id) VALUES (1), (2);
INSERT INTO absence_periods (id, user_id) VALUES
    (77, 1),
    (78, 2),
    (79, 1),
    (80, 1);
'@

$SmokeSql = @'
-- One calculation snapshot is shared by multiple actual transfers.
INSERT INTO vacation_pay_calculation_snapshots (
    id, user_id, absence_period_id,
    absence_status, absence_type_system_code, absence_type_balance_policy,
    absence_coverage, absence_compensation_policy,
    calculation_event_date, vacation_from, vacation_to,
    calculation_status, calculated_gross_amount_minor, payable_calendar_days,
    calculation_schema_version, calculation_currency_code,
    calculation_payload_json, calculation_blocking_reason, snapshot_fingerprint
) VALUES (
    501, 1, 77,
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19',
    'READY', 5000000, 5, 'VACATION_PAY_RECEIPT_CALCULATION_V1', 'RUB',
    '{"schemaVersion":"VACATION_PAY_RECEIPT_CALCULATION_V1","status":"READY"}',
    NULL, repeat('a', 64)
);

-- Two separate incoming transfers for the same vacation are both valid.
INSERT INTO vacation_pay_receipts (
    id, user_id, absence_period_id, calculation_snapshot_id, received_date,
    actual_received_amount_minor, currency_code,
    absence_status, absence_type_system_code, absence_type_balance_policy,
    absence_coverage, absence_compensation_policy,
    calculation_event_date, vacation_from, vacation_to, receipt_fingerprint
) VALUES
(
    1001, 1, 77, 501, DATE '2026-09-10', 3000000, 'RUB',
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19', repeat('b', 64)
),
(
    1002, 1, 77, 501, DATE '2026-09-11', 1200000, 'RUB',
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19', repeat('c', 64)
),
(
    1005, 1, 77, 501, DATE '2026-09-12', 1000, 'USD',
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19', repeat('f', 64)
);

-- A second owner's receipt proves currency totals are kept in separate contexts.
INSERT INTO vacation_pay_receipts (
    id, user_id, absence_period_id, received_date,
    actual_received_amount_minor, currency_code,
    absence_status, absence_type_system_code, absence_type_balance_policy,
    absence_coverage, absence_compensation_policy,
    calculation_event_date, vacation_from, vacation_to, receipt_fingerprint
) VALUES (
    1004, 2, 78, DATE '2026-09-12', 10000, 'USD',
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-20', DATE '2026-09-20', DATE '2026-09-24', repeat('d', 64)
);

-- Partial refunds are real outgoing money movements, in the receipt's currency.
INSERT INTO vacation_pay_receipt_adjustments (
    user_id, receipt_id, adjustment_type, amount_minor, currency_code,
    event_date, reason
) VALUES
    (1, 1001, 'REFUND', 500000, 'RUB', DATE '2026-09-20', 'First partial return'),
    (1, 1001, 'REFUND', 500000, 'RUB', DATE '2026-09-21', 'Second partial return');

-- A correction is atomic: create replacement receipt, then append a VOID event
-- linking the erroneous receipt to its replacement.
BEGIN;
INSERT INTO vacation_pay_receipts (
    id, user_id, absence_period_id, calculation_snapshot_id, received_date,
    actual_received_amount_minor, currency_code,
    absence_status, absence_type_system_code, absence_type_balance_policy,
    absence_coverage, absence_compensation_policy,
    calculation_event_date, vacation_from, vacation_to, receipt_fingerprint
) VALUES (
    1003, 1, 77, 501, DATE '2026-09-11', 1100000, 'RUB',
    'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
    DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19', repeat('e', 64)
);
INSERT INTO vacation_pay_receipt_adjustments (
    user_id, receipt_id, adjustment_type, reason, replacement_receipt_id
) VALUES (
    1, 1002, 'VOID', 'Corrected erroneous amount', 1003
);
COMMIT;

DO $$
DECLARE
    rejected BOOLEAN;
    actual_count INTEGER;
    active_gross NUMERIC(30, 0);
    active_refunds NUMERIC(30, 0);
    active_net NUMERIC(30, 0);
    snapshot_count INTEGER;
BEGIN
    SELECT count(*) INTO actual_count
      FROM vacation_pay_receipts
     WHERE user_id = 1 AND absence_period_id = 77;
    IF actual_count <> 4 THEN
        RAISE EXCEPTION 'Expected four transfer rows for one vacation; found %', actual_count;
    END IF;

    SELECT count(*) INTO snapshot_count
      FROM vacation_pay_calculation_snapshots
     WHERE user_id = 1 AND absence_period_id = 77;
    IF snapshot_count <> 1 THEN
        RAISE EXCEPTION 'Expected one shared calculation snapshot; found %', snapshot_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM vacation_pay_receipts
         WHERE id = 1005 AND currency_code = 'USD'
           AND calculation_snapshot_id = 501
    ) THEN
        RAISE EXCEPTION 'Actual receipt currency must remain separate from calculation currency';
    END IF;

    SELECT COALESCE(SUM(r.actual_received_amount_minor), 0),
           COALESCE(SUM(a.refunded), 0),
           COALESCE(SUM(r.actual_received_amount_minor - a.refunded), 0)
      INTO active_gross, active_refunds, active_net
      FROM vacation_pay_receipts r
      LEFT JOIN LATERAL (
          SELECT COALESCE(SUM(amount_minor), 0) AS refunded
            FROM vacation_pay_receipt_adjustments
           WHERE receipt_id = r.id AND adjustment_type = 'REFUND'
      ) a ON TRUE
     WHERE r.user_id = 1
       AND r.absence_period_id = 77
       AND r.currency_code = 'RUB'
       AND NOT EXISTS (
           SELECT 1 FROM vacation_pay_receipt_adjustments v
            WHERE v.receipt_id = r.id AND v.adjustment_type = 'VOID'
       );

    IF active_gross <> 4100000 OR active_refunds <> 1000000 OR active_net <> 3100000 THEN
        RAISE EXCEPTION 'Unexpected RUB ledger totals: gross %, refunds %, net %',
            active_gross, active_refunds, active_net;
    END IF;

    IF (
        SELECT COALESCE(SUM(actual_received_amount_minor), 0)
          FROM vacation_pay_receipts
         WHERE user_id = 1 AND absence_period_id = 77
           AND currency_code = 'USD'
           AND NOT EXISTS (
               SELECT 1 FROM vacation_pay_receipt_adjustments a
                WHERE a.receipt_id = vacation_pay_receipts.id
                  AND a.adjustment_type = 'VOID'
           )
    ) <> 1000 THEN
        RAISE EXCEPTION 'USD receipt must remain in a separate currency bucket';
    END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, amount_minor, currency_code,
            event_date, reason
        ) VALUES (1, 1001, 'REFUND', 2100000, 'RUB', DATE '2026-09-22', 'Over-refund');
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Over-refund was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, amount_minor, currency_code,
            event_date, reason
        ) VALUES (1, 1002, 'REFUND', 100, 'RUB', DATE '2026-09-22', 'Refund after void');
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Refund after VOID was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, reason
        ) VALUES (1, 1001, 'VOID', 'Void after refund');
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'VOID after refund was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, reason
        ) VALUES (1, 1002, 'VOID', 'Duplicate void');
    EXCEPTION WHEN unique_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Duplicate VOID was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, reason, replacement_receipt_id
        ) VALUES (1, 1005, 'VOID', 'Replacement from another vacation', 1004);
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Cross-vacation replacement was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipts (
            user_id, absence_period_id, received_date, actual_received_amount_minor,
            currency_code, absence_status, absence_type_system_code,
            absence_type_balance_policy, absence_coverage, absence_compensation_policy,
            calculation_event_date, vacation_from, vacation_to, receipt_fingerprint
        ) VALUES (
            2, 79, DATE '2026-09-10', 100, 'RUB',
            'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
            DATE '2026-09-15', DATE '2026-09-15', DATE '2026-09-19', repeat('f', 64)
        );
    EXCEPTION WHEN foreign_key_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Cross-owner absence reference was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipts (
            user_id, absence_period_id, calculation_snapshot_id, received_date,
            actual_received_amount_minor, currency_code,
            absence_status, absence_type_system_code, absence_type_balance_policy,
            absence_coverage, absence_compensation_policy,
            calculation_event_date, vacation_from, vacation_to, receipt_fingerprint
        ) VALUES (
            2, 78, 501, DATE '2026-09-10', 100, 'USD',
            'APPROVED', 'VACATION', 'VACATION_DAYS', 'FULL_DAY', 'VACATION_ALLOWANCE',
            DATE '2026-09-20', DATE '2026-09-20', DATE '2026-09-24', repeat('1', 64)
        );
    EXCEPTION WHEN foreign_key_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Cross-owner calculation snapshot reference was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        INSERT INTO vacation_pay_receipt_adjustments (
            user_id, receipt_id, adjustment_type, amount_minor, currency_code,
            event_date, reason
        ) VALUES (1, 1001, 'REFUND', 100, 'USD', DATE '2026-09-22', 'Wrong refund currency');
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Refund currency mismatch was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        UPDATE vacation_pay_receipts SET actual_received_amount_minor = 1 WHERE id = 1001;
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Receipt UPDATE was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        DELETE FROM vacation_pay_receipts WHERE id = 1001;
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Receipt DELETE was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        EXECUTE 'TRUNCATE TABLE vacation_pay_receipt_adjustments';
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Adjustment TRUNCATE was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        UPDATE vacation_pay_calculation_snapshots SET calculated_gross_amount_minor = 1 WHERE id = 501;
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Calculation snapshot UPDATE was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        UPDATE vacation_pay_receipt_adjustments SET reason = 'tampered' WHERE receipt_id = 1001;
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Adjustment UPDATE was not rejected'; END IF;

    rejected := FALSE;
    BEGIN
        DELETE FROM vacation_pay_receipt_adjustments WHERE receipt_id = 1001;
    EXCEPTION WHEN SQLSTATE '55000' THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN RAISE EXCEPTION 'Adjustment DELETE was not rejected'; END IF;
END;
$$;

SELECT 'PASS: multi-transfer ledger, shared calculation snapshot, correction VOID, partial refunds, owner isolation, over-refund guards, and append-only triggers' AS result;
'@

try {
    [System.IO.File]::WriteAllText($BaselineFile, $BaselineSql, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($SmokeFile, $SmokeSql, [System.Text.UTF8Encoding]::new($false))

    Write-Host "=== STARTING ISOLATED POSTGRESQL 16 ==="
    & docker run --detach --rm --name $Container --env POSTGRES_PASSWORD=phase5b1_test_only --env POSTGRES_DB=dutylog_phase5b1 postgres:16-alpine
    if ($LASTEXITCODE -ne 0) { throw "Could not start isolated PostgreSQL container" }

    $Ready = $false
    for ($Attempt = 0; $Attempt -lt 60; $Attempt++) {
        & $env:ComSpec /d /c "docker exec $Container pg_isready -U postgres -d dutylog_phase5b1 >NUL 2>&1"
        if ($LASTEXITCODE -eq 0) { $Ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$Ready) { throw "PostgreSQL did not become ready within 60 seconds" }

    Write-Host "=== CREATING MINIMAL PRE-V92 BASELINE ==="
    & docker cp $BaselineFile "$($Container):/tmp/baseline.sql"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy baseline SQL into PostgreSQL container" }
    & docker exec $Container psql -X -v ON_ERROR_STOP=1 -U postgres -d dutylog_phase5b1 -f /tmp/baseline.sql
    if ($LASTEXITCODE -ne 0) { throw "Could not create pre-V92 baseline schema" }

    Write-Host "=== APPLYING THE EXACT V92 MIGRATION ==="
    & docker cp $Migration "$($Container):/tmp/V92__vacation_pay_receipts.sql"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy V92 migration into PostgreSQL container" }
    & docker exec $Container psql -X -v ON_ERROR_STOP=1 -U postgres -d dutylog_phase5b1 -f /tmp/V92__vacation_pay_receipts.sql
    if ($LASTEXITCODE -ne 0) { throw "V92 failed on PostgreSQL 16" }

    Write-Host "=== VERIFYING LEDGER CONSTRAINTS AND IMMUTABILITY ==="
    & docker cp $SmokeFile "$($Container):/tmp/smoke.sql"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy smoke SQL into PostgreSQL container" }
    & docker exec $Container psql -X -v ON_ERROR_STOP=1 -U postgres -d dutylog_phase5b1 -f /tmp/smoke.sql
    if ($LASTEXITCODE -ne 0) { throw "V92 PostgreSQL ledger assertions failed" }

    Write-Host "`nPOSTGRESQL V92 LEDGER SMOKE TEST PASSED"
}
finally {
    Write-Host "=== CLEANING UP TEMPORARY POSTGRESQL ==="
    & $env:ComSpec /d /c "docker rm --force $Container >NUL 2>&1"
    Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
