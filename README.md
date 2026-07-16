# cloud-itonami-isic-9820

**Undifferentiated Service-Producing Activities of Private Households for Own Use** — ISIC Rev.4 class 9820.

A coordination-only actor for a household time-use-survey programme, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Scope (read this first — this class is unusual)

ISIC 9820 is a UN System of National Accounts bookkeeping category for
**own-account, non-market service production**: unpaid domestic and care
work a household performs for its own members (cooking, cleaning,
childcare, eldercare, and similar). It is counted in satellite national
accounts for statistical completeness — it is not part of GDP, and it is
**not a market transaction**. There is no real-world "business" that IS a
9820 entity in the way, say, a restaurant IS an ISIC 5610 entity.

Rather than inventing a fictitious commercial business for this class,
this actor is scoped honestly as an **operations-coordination actor for a
structured unpaid-household-work time-use survey/tracking programme** —
the kind of genuine, precedented activity a national statistics office
runs to measure the care economy (see UN Statistics Division / OECD
time-use survey methodology). Concretely, it coordinates the back-office
operations of such a programme: logging participating households'
self-reported time-use-diary data, scheduling enumerator household
visits, coordinating programme support (survey materials, enumerator
training), and flagging welfare concerns an enumerator observes during a
visit for human triage.

**This is a data-coordination/statistical-support use case, not a
marketplace.** It does not employ, pay, or supervise household workers;
it does not sell household services; and it never finalizes any
welfare-intervention decision (see the CRITICAL exclusion below). The
sibling ISIC 9810 class (own-account **goods** production by households)
is expected to use the parallel framing for the goods side — this actor
coordinates conceptually with it but does not depend on its code.

## Features

- **Closed proposal-op allowlist**: `log-time-use-record`,
  `schedule-survey-visit`, `coordinate-programme-support`,
  `flag-welfare-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Household unverified** — target must exist AND be independently
     registered/verified in the store before any proposal proceeds.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Op not in closed allowlist** — an op the advisor was never
     authorized to propose is rejected.
  4. **Welfare-intervention finalization** — any proposal, regardless of
     op, that uses finalization/execution-ACTION language for a
     welfare-intervention decision (child/elder protective action,
     custody removal, protective-order issuance) is permanently blocked.
     **This actor's op allowlist never includes anything that directly
     finalizes a welfare-intervention decision — `flag-welfare-concern`
     only surfaces a concern for human triage and always escalates; it
     is never an auto-commit-eligible op at any phase.**
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: time-use-record logging only (approval-gated)
  - Phase 2: + survey-visit scheduling, programme-support coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals below the cost
    threshold (welfare concerns always escalate; over-threshold support
    coordination always escalates)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/timeuseops/governor_test.clj` — unit tests of governor hard checks, welfare-intervention-finalization block, and the dedicated self-trip regression test
- `test/timeuseops/advisor_test.clj` — advisor proposal shape and consistency
- `test/timeuseops/phase_test.clj` — rollout phase logic
- `test/timeuseops/governor_contract_test.clj` — full graph integration, audit trail
- `test/timeuseops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `timeuseops.store` — SSoT (MemStore, String-keyed programme-household directory, append-only ledger)
- `timeuseops.advisor` — contained intelligence node (mock + real-LLM seam)
- `timeuseops.governor` — independent compliance layer
- `timeuseops.phase` — staged rollout (0→3)
- `timeuseops.operation` — langgraph-clj StateGraph
- `timeuseops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See ADR-2607121000, ADR-2607152500, and the ADR that landed this specific class (`cloud-itonami-isic-9820-undifferentiated-household-services-coverage`) for design decisions.
