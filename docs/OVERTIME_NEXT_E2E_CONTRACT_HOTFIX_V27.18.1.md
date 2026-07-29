# Overtime Next E2E Contract Hotfix — v27.18.1

## Причина

Overtime Next intentionally renders the same ledger actions twice: a desktop table and mobile cards. CSS chooses the visible presentation. The existing integrity scenario used an unscoped `.first()` selector and therefore targeted the first DOM match — a hidden mobile delete button during a desktop run.

The trend also intentionally changes its key contract by range size:

```text
All time / Year -> YYYY-MM
Month           -> YYYY-MM-DD
```

The browser scenario still expected a daily key while All-time was active.

## Исправление

- Desktop delete and post-delete assertions are scoped to `#ledgerRows`.
- The delete button must be visible before click.
- All-time trend is asserted by a monthly key.
- Month mode is then selected and asserted by a daily key.
- Existing Year, desktop-table and mobile-card checks remain.

## Границы

- no production JavaScript/CSS behavior change;
- no accounting/FIFO change;
- no domain API change;
- no database migration;
- Flyway remains V36;
- baseline remains 96 Java classes / 500 `@Test` methods / 26 Playwright scenarios.
