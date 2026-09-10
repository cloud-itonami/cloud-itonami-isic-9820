(ns timeuseops.advisor
  "TimeUseProgrammeAdvisor -- the *contained intelligence node* for the
  ISIC-9820 undifferentiated-household-services (own-account, non-market
  domestic/care work) time-use-survey-programme operations-coordination
  actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: time-use-diary record logging, enumerator household-visit
  scheduling, programme-support (survey-material/training) coordination,
  and welfare-concern flagging. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation -- every
  proposal's `:effect` is always `:propose`. Every output is censored
  downstream by `timeuseops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a welfare-intervention finalization (child
  protective action, elder/adult protective action, custody removal,
  protective-order issuance) -- that is permanently out of scope for this
  actor, not merely un-implemented. `timeuseops.governor`'s
  `welfare-intervention-finalization-violations` independently re-scans
  every proposal for exactly this failure mode (a compromised or confused
  advisor drifting into scope it must never touch) and HARD-holds it,
  regardless of confidence or op.

  NOTE on scope-exclusion phrasing (a known self-tripping bug pattern in
  this actor family): the governor's scope-exclusion term list is phrased
  as the finalization/execution ACTION ('finalize welfare intervention'),
  never as a bare noun ('welfare'), precisely because this advisor's own
  legitimate `:flag-welfare-concern` disclaimer text below must be free to
  use words like 'welfare'/'concern'/'safety' without ever self-tripping
  the scope-exclusion gate on its own happy path. See
  `timeuseops.governor-test`'s dedicated
  `default-advisor-proposals-never-self-trip-scope-exclusion` test.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op          kw             ; echoes the request op
     :household-id str
     :summary     str            ; human-facing draft / finding
     :rationale   str            ; why -- SCANNED by the scope-exclusion gate
     :cites       [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect      :propose       ; ALWAYS :propose -- never a direct actuation
     :value       map            ; the draft payload a human/system would review
     :confidence  0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-time-use-record
  "Draft a time-use-diary log entry: unpaid domestic/care-work hours the
  household reported for itself (cooking, cleaning, childcare, eldercare,
  etc.) -- pure statistical record-keeping, never a judgment about the
  household or its members."
  [_db {:keys [household-id patch]}]
  {:op          :log-time-use-record
   :household-id household-id
   :summary     (str household-id " の無償労働時間使用データを記録: " (pr-str (keys patch)))
   :rationale   "世帯が自己申告した無償の家事・育児・介護等の時間使用調査データの記録のみ。統計目的の調査記録であり、世帯員個人への評価や処遇の判断は一切行わない。"
   :cites       [household-id]
   :effect      :propose
   :value       (merge {:household-id household-id} patch)
   :confidence  0.93})

(defn- propose-survey-visit
  "Draft an enumerator household-visit scheduling proposal (a calendar
  entry, never a direct dispatch or a compulsory summons)."
  [_db {:keys [household-id patch]}]
  {:op          :schedule-survey-visit
   :household-id household-id
   :summary     (str household-id " への調査員訪問日程を提案: " (pr-str (keys patch)))
   :rationale   "調査員による家庭訪問日程の調整提案のみ。訪問の実施可否は世帯の任意であり、最終判断は世帯と調査員が行う。"
   :cites       [household-id]
   :effect      :propose
   :value       (merge {:household-id household-id} patch)
   :confidence  0.88})

(defn- propose-programme-support
  "Draft a programme-support coordination proposal (survey-material
  distribution such as diary booklets, enumerator training coordination --
  never a cash transfer, benefit, or compensation decision)."
  [_db {:keys [household-id patch]}]
  {:op          :coordinate-programme-support
   :household-id household-id
   :summary     (str household-id " に関連するプログラム支援調整を提案: " (pr-str (keys patch)))
   :rationale   "調査用ダイアリー資材の配布や調査員研修などプログラム運営支援の調整のみ。世帯への金銭給付や補償の決定は行わない。"
   :cites       [household-id]
   :effect      :propose
   :value       (merge {:household-id household-id} patch)
   :confidence  0.90})

(defn- propose-welfare-concern
  "Surface a domestic-safety or child/elder-welfare concern observed during
  a survey visit, for HUMAN triage. This op ALWAYS escalates in
  `timeuseops.governor` -- never auto-committed at any phase -- regardless
  of how confident the advisor is that the concern is real. It NEVER
  proposes to finalize any welfare-intervention decision itself -- see
  `timeuseops.governor`'s `welfare-intervention-finalization-violations`,
  a separate, permanent, un-overridable block."
  [_db {:keys [household-id patch]}]
  {:op          :flag-welfare-concern
   :household-id household-id
   :summary     (str household-id " の福祉懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale   "調査員が訪問時に観察した世帯の安全・福祉に関する懸念事実の報告のみ。福祉介入の要否判断は常に人間が行い、この提案自体は介入を確定も実行もしない。"
   :cites       [household-id]
   :effect      :propose
   :value       (merge {:household-id household-id} patch)
   :confidence  (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-time-use-record (propose-time-use-record _db request)
                   :schedule-survey-visit (propose-survey-visit _db request)
                   :coordinate-programme-support (propose-programme-support _db request)
                   :flag-welfare-concern (propose-welfare-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's welfare-intervention-finalization block end-to-end. Must
    ;; be cleared before production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually moved to finalize welfare intervention for this household")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t           :advisor-proposal
   :op          (:op proposal)
   :household-id (:household-id proposal)
   :summary     (:summary proposal)
   :confidence  (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
