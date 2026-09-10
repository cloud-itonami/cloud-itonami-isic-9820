(ns timeuseops.store
  "SSoT for the ISIC-9820 undifferentiated-household-services COORDINATION
  actor, behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  IMPORTANT FRAMING (see also the top-level README's 'Scope' section): ISIC
  9820 (Undifferentiated service-producing activities of private households
  for own use) is a UN System of National Accounts bookkeeping category for
  own-account, non-market service production -- unpaid domestic and care
  work performed within a household for its own members (cooking, cleaning,
  childcare, eldercare, etc.), counted in satellite accounts for statistical
  completeness, not GDP. There is no real-world 'business' that IS a 9820
  entity. This actor therefore does not coordinate a marketplace -- it
  coordinates the back-office operations of a national-statistics-office
  style TIME-USE SURVEY / TRACKING PROGRAMME (UN/OECD time-use survey
  methodology) that registers and tracks participating households' unpaid
  domestic/care-work hours: time-use-diary logging, enumerator household-visit
  scheduling, programme-support (survey material / enumerator training)
  coordination, and welfare-concern flagging for human triage. It never
  touches medication/clinical decisions, child/elder-welfare case work, or
  any welfare-intervention finalization -- see `timeuseops.governor`'s
  `welfare-intervention-finalization-violations`, a HARD, permanent,
  un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `households` directory keyed by `:household-id` STRING (never
  a keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified programme-enrollment household record must exist
  before ANY proposal for that household may ever commit or escalate --
  `timeuseops.governor`'s `household-unverified-violations` re-derives this
  from the household's own `:registered?`/`:verified?` fields, never from
  proposal self-report, the SAME 'ground truth, not self-report' discipline
  every sibling actor's own governor uses.

  The ledger stays append-only: which household a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (household [s household-id] "Registered programme household record, or nil.
    Household map: {:household-id .. :name .. :registered? bool :verified? bool}.")
  (all-households [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-households [s households] "replace/seed the household directory (map household-id->household)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained programme-household directory covering both the
  happy path and the governor's own hard checks, so the actor + tests run
  offline."
  []
  {:households
   {"household-1" {:household-id "household-1" :name "Tanaka family (Ward 3 sample cell)"
                    :registered? true :verified? true}
    "household-2" {:household-id "household-2" :name "Silva family (Ward 7 sample cell)"
                    :registered? true :verified? true}
    "household-3" {:household-id "household-3" :name "Nakamura family (Ward 3, in intake)"
                    :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (household [_ household-id] (get-in @a [:households household-id]))
  (all-households [_] (sort-by :household-id (vals (:households @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-households [s households] (when (seq households) (swap! a assoc :households households)) s))

(defn seed-db
  "A MemStore seeded with the demo programme-household directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `households` map (household-id string ->
  household map) -- the primary test/dev entry point. `households` may be
  empty (an unregistered-everywhere store)."
  [households]
  (->MemStore (atom {:households (or households {}) :ledger [] :coordination-log []})))
