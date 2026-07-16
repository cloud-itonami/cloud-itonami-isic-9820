(ns timeuseops.governor
  "TimeUseProgrammeGovernor -- the independent compliance layer that earns
  the TimeUseProgrammeAdvisor the right to commit. The advisor has no
  notion of whether a household is actually registered and verified in the
  survey programme, whether its own proposed `:effect` secretly claims a
  direct actuation instead of a mere proposal, or whether it has silently
  drifted into a permanently out-of-scope decision area, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY for a
  national-statistics-office style time-use survey/tracking programme
  (time-use-diary logging, enumerator visit scheduling, programme-support
  coordination, welfare-concern flagging). It NEVER performs or authorizes:
    - finalizing a welfare-intervention decision of any kind (child
      protective action, elder/adult protective action, custody removal,
      protective-order issuance)
    - clinical/medical decisions
    - any direct actuation (every proposal must be `:effect :propose`)

  Three HARD checks, ALL permanent, un-overridable by any human approval:

    1. Household unverified        -- the target household's programme-
                                       enrollment record must exist AND be
                                       independently confirmed
                                       `:registered?`/`:verified?` in the
                                       store before ANY proposal for it may
                                       commit or even escalate. Never trusts
                                       a proposal's own claim about the
                                       household -- re-derived from the
                                       household's own store record, the
                                       same 'ground truth, not self-report'
                                       discipline every sibling actor's
                                       governor uses.
    2. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not merely
                                       low-confidence.
    3. Op not in closed allowlist  -- an op outside the four-op allowlist
                                       is, by construction, an advisor
                                       proposing something it was never
                                       authorized to propose -- HARD block.

  A FOURTH HARD, PERMANENT check, evaluated unconditionally on every
  proposal regardless of op or confidence, and never overridable by any
  human approval:

    4. Welfare-intervention finalization -- ANY proposal (regardless of
       op) whose op, rationale, summary, citations or draft value uses
       finalization/execution-ACTION language for a welfare intervention
       (finalizing/authorizing/executing a child- or elder/adult-
       protective action, a custody removal, or a protective-order
       issuance) is a HARD, PERMANENT block -- this actor's charter
       structurally excludes ever finalizing such a decision, not as a
       rollout milestone.

       CRITICAL implementation note (a known self-tripping bug pattern in
       this actor family, hit and independently fixed by multiple sibling
       actors before this one): the scanned terms below are phrased as the
       finalization/execution ACTION ('finalize welfare intervention'),
       never as a bare noun ('welfare'). A bare-noun term would match
       inside this actor's OWN legitimate `:flag-welfare-concern`
       disclaimer text (which must be free to say things like 'welfare
       concern' / 'safety' / 'child' while explicitly disclaiming that it
       does NOT finalize any intervention) and cause the actor to
       self-block its own happy path. See
       `timeuseops.governor-test`'s dedicated
       `default-advisor-proposals-never-self-trip-scope-exclusion` test,
       which asserts the default mock advisor's own proposals for every
       allowed op never trip this check.

  Two ESCALATE (SOFT) gates, both requiring human sign-off:
    - `:flag-welfare-concern` ALWAYS escalates, regardless of confidence,
      regardless of how clean the proposal otherwise is. It must NEVER be
      auto-commit-eligible -- `timeuseops.phase` independently agrees:
      `:flag-welfare-concern` is never a member of any phase's `:auto` set
      either -- two layers, not one.
    - `:coordinate-programme-support` whose draft `:value` `:cost` exceeds
      `support-cost-threshold` escalates for human budget sign-off.
    - LLM confidence below `confidence-floor` also always escalates."
  (:require [clojure.string :as str]
            [timeuseops.store :as store]))

(def confidence-floor 0.6)

(def support-cost-threshold
  "Programme-support coordination proposals (survey material / enumerator
  training) whose draft `:cost` exceeds this always escalate for human
  budget sign-off, regardless of confidence."
  300)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `op-not-allowed-violations`)."
  #{:log-time-use-record :schedule-survey-visit
    :coordinate-programme-support :flag-welfare-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-welfare-concern})

(def welfare-intervention-finalization-terms
  "Case-insensitive substrings marking a proposal as directly finalizing or
  executing a welfare-intervention decision -- permanently out of scope for
  this coordination-only actor. Phrased as the finalization/execution
  ACTION, never as a bare noun (see the namespace docstring's CRITICAL
  implementation note on the self-tripping bug this avoids). Scanned across
  the proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent."
  ["finalize welfare intervention" "finalize the welfare intervention"
   "finalize a welfare intervention" "authorize welfare intervention"
   "execute welfare intervention" "welfare intervention decision finalized"
   "finalize child protective action" "authorize child protective action"
   "execute child protective action" "finalize elder protective action"
   "authorize elder protective action" "execute elder protective action"
   "finalize adult protective action" "authorize adult protective action"
   "finalize custody removal" "authorize custody removal"
   "execute custody removal" "remove the child from the home"
   "issue a protective order" "finalize a protective order"
   "authorize a protective order"
   "福祉介入を確定" "福祉介入を実行" "福祉介入の最終決定" "福祉介入を執行"
   "児童保護措置を確定" "児童保護措置を執行" "高齢者保護措置を確定"
   "高齢者保護措置を執行" "保護命令の発令を確定" "親権はく奪を確定"])

;; ----------------------------- checks -----------------------------

(defn- household-unverified-violations
  "The target household's programme-enrollment record must exist AND be
  independently `:registered?`/`:verified?` in the store -- never trust the
  proposal's own `:household-id` claim without a store lookup."
  [{:keys [household-id]} st]
  (let [h (store/household st household-id)]
    (when-not (and h (:registered? h) (:verified? h))
      [{:rule :household-unverified
        :detail (str household-id " は未登録または未検証のプログラム世帯 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- op-not-allowed-violations
  "An op outside the closed four-op allowlist is a scope violation by
  construction."
  [proposal]
  (when-not (contains? allowed-ops (:op proposal))
    [{:rule :op-not-allowed
      :detail (str (pr-str (:op proposal)) " は許可された操作(closed allowlist)に含まれない")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the welfare-intervention-finalization scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- welfare-intervention-finalization-violations
  "HARD, PERMANENT block: a proposal whose content uses finalization/
  execution-ACTION language for a welfare-intervention decision, regardless
  of confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal, independent of `op-not-allowed-
  violations` -- an otherwise-allowed op (e.g. `:flag-welfare-concern`)
  that drifts into finalization language is caught here too."
  [proposal]
  (let [blob (text-blob proposal)]
    (when (some #(str/includes? blob %) welfare-intervention-finalization-terms)
      [{:rule :welfare-intervention-finalization-blocked
        :detail "福祉介入(児童・高齢者保護措置/親権関連の強制措置/保護命令等)を確定・実行する提案は永久に禁止 -- この提案自体が介入を finalize してはならない"}])))

(defn- support-cost-exceeds-threshold?
  [proposal]
  (and (= :coordinate-programme-support (:op proposal))
       (number? (get-in proposal [:value :cost]))
       (> (get-in proposal [:value :cost]) support-cost-threshold)))

(defn check
  "Censors a TimeUseProgrammeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [household-id (or (:household-id proposal) (:household-id request))
        hard (into []
                   (concat (household-unverified-violations {:household-id household-id} store)
                           (effect-not-propose-violations proposal)
                           (op-not-allowed-violations proposal)
                           (welfare-intervention-finalization-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (support-cost-exceeds-threshold? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t           :governor-hold
   :op          (:op request)
   :actor       (:actor-id context)
   :household-id (:household-id request)
   :disposition :hold
   :basis       (mapv :rule (:violations verdict))
   :violations  (:violations verdict)
   :confidence  (:confidence verdict)})
