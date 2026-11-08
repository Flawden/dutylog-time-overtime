# DutyLog — Phase 5B.1 financial ledger hardening

This is an unreleased replacement design for the local, uncommitted Phase 5B.1 receipt slice. V92 is still local and must be replaced in-place before it is ever applied to a persistent database.

## Financial model

- One `vacation_pay_receipts` row represents exactly one actual incoming bank transfer.
- Multiple incoming transfers may belong to the same approved annual-paid-vacation absence.
- Each incoming amount is net cash actually credited after withholding.
- Gross calculation evidence lives in `vacation_pay_calculation_snapshots`, separately from cash movements.
- A calculation snapshot is immutable and may be referenced by multiple transfers. New calculation attempts create new snapshots; the latest matching snapshot is attached to a later minimal receipt.
- If the absence identity changes after a snapshot was created, a new minimal transfer is still recordable, but the stale snapshot is not attached.
- Receipt and calculation currencies are kept independently. No conversion or gross/net comparison is inferred.

## Append-only adjustment model

`vacation_pay_receipt_adjustments` stores two distinct event types:

- `VOID`: accounting cancellation of a mistaken receipt. It is not a cash movement. It may reference a replacement receipt when used by the correction operation.
- `REFUND`: a real outgoing transfer back to the employer. It has its own date and positive amount, in the original receipt currency. Multiple partial refunds are allowed, but their total cannot exceed the original receipt amount.

A correction is one transaction: create a replacement incoming receipt, then append a `VOID` event linking the original to the replacement. If either insert fails, the transaction rolls back. A receipt that already has any adjustment cannot be corrected or voided again. A voided receipt cannot be refunded, and a receipt with refunds cannot later be voided. Actual refunds are never disguised as accounting cancellations.

The database trigger locks the original receipt while validating an adjustment. This serializes concurrent partial refunds and prevents their combined amount from exceeding the original transfer.

## API

- `POST /api/payroll/vacation-pay/receipts` — record one incoming transfer.
- `GET /api/payroll/vacation-pay/receipts` — list incoming transfers.
- `GET /api/payroll/vacation-pay/receipts/summary` — aggregate non-voided receipts by absence and currency, subtracting actual refunds.
- `GET /api/payroll/vacation-pay/receipts/{id}/history` — receipt with immutable adjustment history and current status.
- `POST /api/payroll/vacation-pay/receipts/{id}/cancel` — append a VOID event.
- `POST /api/payroll/vacation-pay/receipts/{id}/correct` — create replacement receipt and append linked VOID event atomically.
- `POST /api/payroll/vacation-pay/receipts/{id}/refund` — append an actual outgoing REFUND event.

## Database integrity

V92 creates separate immutable calculation snapshots, incoming receipts, and adjustment events. It enforces:
- same-owner and same-absence foreign-key relationships;
- multiple receipts per absence;
- calculation snapshot linkage to the same owner and absence;
- at most one VOID event per receipt;
- no VOID after a refund and no refund after VOID;
- refund currency equal to the original receipt currency;
- cumulative refunds not exceeding the original receipt amount;
- same-owner, same-absence replacement linkage;
- rejection of UPDATE, DELETE, and TRUNCATE on all three ledger tables.

The append-only boundary protects against ordinary application-role DML. A database owner/superuser can still alter or disable schema objects and is outside this integrity boundary.

## Apply and verify

Do not apply this patch over a database where the earlier draft V92 has already been migrated. The user has confirmed V92 is still unreleased and uncommitted; replace the local V92 file before any persistent database migration.

Run focused tests:

```powershell
mvn '-Dtest=VacationPayReceiptServiceTest,VacationPayReceiptLedgerServiceTest,VacationPayReceiptControllerTest' test
```

Run the isolated PostgreSQL 16 ledger smoke test:

```powershell
.\scripts\phase5b1\verify-v92-postgresql.ps1
```

The smoke test starts a disposable PostgreSQL 16 Docker container, creates only prerequisite `users` and `absence_periods` baseline tables, applies the exact V92 file, and verifies multiple transfers, shared calculation snapshots, currency separation, correction links, partial refunds, over-refund rejection, owner isolation, and append-only triggers. It does not connect to the configured application database.

A separate full Flyway V1–V92 test against a fresh database remains a release gate. The isolated V92 smoke test does not replace it.
