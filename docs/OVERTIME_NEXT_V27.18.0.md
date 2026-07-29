# Overtime Next — v27.18.0

## Product goal

Overtime must answer four questions without forcing the user to read a spreadsheet:

1. How many hours are available now?
2. How much was earned and used?
3. Which credit will FIFO consume next?
4. What exactly happened in the selected period?

The accounting engine remains authoritative and unchanged. This release replaces only the presentation layer.

## Screen contract

```text
Global account summary
→ available balance
→ total earned
→ total used
→ usage ratio
→ next FIFO credit

Selected period
→ Month / Year / All time / custom range
→ earned-versus-used trend
→ filtered metrics
→ paginated ledger
```

## Responsive contract

Desktop keeps the professional table with dates, projected/source intervals, earned, used, allocation provenance, remaining balance and actions.

At `max-width: 760px` the table is not compressed or horizontally scrolled. It is replaced by credit cards containing:

- date and status;
- earned amount;
- interval/timezone projection;
- reason;
- used/remaining progress;
- expandable FIFO allocation details;
- edit/delete actions.

Both presentations consume the same `state.ledgerPage.items` and use the same editor functions.

## FIFO contract

The queue is derived from unique source credits, not daily projection fragments. Open credits are ordered by exact credited/source instant with date/id fallback. The first item is the next balance consumed by a new usage.

UI grouping never rebuilds, mutates or simulates FIFO. The backend remains the only accounting authority.

## Trend contract

- ranges of 45 days or fewer group by day;
- longer/all-time ranges group by month;
- at most 31 day columns or 12 month columns are shown;
- earned values use the source credit date while used values use the actual time-off date;
- date-range filtering is applied independently to credits and usages;
- no chart dependency is introduced.

## Compatibility

- no database migration;
- Flyway remains V36;
- no domain API change;
- existing credit/usage/scenario editors remain;
- legacy timezone migration remains;
- CSV and Excel-compatible exports remain;
- Today Dashboard continues to open the overtime workspace.

## Regression coverage

- `OvertimeNextFrontendContractTest` — static HTML/JS/CSS contract;
- `overtime-next.spec.js` — 5 earned, 4 used, 1 available, FIFO next-credit, year filter, desktop table and phone cards;
- existing overtime daily projection, modal, FIFO integrity and scenario-manager E2E tests remain mandatory.
