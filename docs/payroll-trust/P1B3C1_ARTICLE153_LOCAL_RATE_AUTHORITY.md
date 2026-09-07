# P1B3C1 — Article 153 source-backed local rate authority

## Purpose

P1B3C1 closes the next economic authority blocker without creating a second
pricing engine.

Existing DutyLog `PayPricingTerm` / `PayPricingRule` remains the economic
configuration and already supports the `HOLIDAY` dimension as an additive
premium over ordinary base pay.

P1B3C1 adds a legally loaded sidecar proving that one exact effective HOLIDAY
rule set is backed by a source Article 153 allows to establish the concrete
rate:

- `COLLECTIVE_AGREEMENT`;
- `LOCAL_NORMATIVE_ACT`;
- `EMPLOYMENT_CONTRACT`.

The sidecar freezes a SHA-256 fingerprint of the exact HOLIDAY rules. Mutation
of a certified rule set fails closed; correction is a new effective pricing
term and new certification rather than historical rewrite.

## Resolution semantics

For the source work date:

1. resolve the effective `PayPricingTerm`;
2. if it contains no HOLIDAY rules, the explicit term is `STATUTORY_ONLY`;
3. if HOLIDAY rules exist without source certification, block;
4. if certified fingerprint differs from current rules, block;
5. otherwise reuse `PayPricingRuleResolver` to calculate the configured
   additive HOLIDAY premium BPS.

The configured local premium does **not** replace the federal floor. Final
Article 153 pricing must later apply the statutory floor from
`Article153EconomicLegalPolicy` and use the source-backed local amount only
where it is more favorable / otherwise applicable.

## Legal source lock

Current implementation source lock:

- TK RF Article 153, current 2026 code revision;
- Constitutional Court RF 28.06.2018 No. 26-P for the remuneration-system
  boundary handled in the next stage.

## Scope boundary

P1B3C1 does not:

- activate `HOLIDAY_PAY`;
- wire Article 153 into `PayrollService`;
- calculate Payroll money;
- change P1B3A qualified work;
- change P1B3B1 statutory floor;
- change P1B3B2A monthly norm authority;
- change P1B3B2B rest-day election;
- change generic PayPricing rules or their country-neutral engine;
- freeze final Article 153 Payroll snapshot provenance;
- solve remuneration-system compensation/stimulating components.

The next economic stage is P1B3C2 remuneration-system component authority.
