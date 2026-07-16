# Contributing to cloud-itonami-isic-9820

Contributions should preserve the actor's scope: back-office time-use-
survey-programme coordination only, with the CRITICAL exclusion of ever
finalizing a welfare-intervention decision (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize, authorize, or execute any welfare-intervention decision
  (child protective action, elder/adult protective action, custody
  removal, protective-order issuance) — `flag-welfare-concern` only
  surfaces a concern for human triage and always escalates.
- Make clinical/medical decisions.
- Directly actuate anything — every proposal's `:effect` must be
  `:propose`.

Contributions that cross these boundaries will be rejected.
