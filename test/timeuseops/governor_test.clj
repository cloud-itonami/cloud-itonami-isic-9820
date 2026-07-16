(ns timeuseops.governor-test
  "Pure unit tests of `timeuseops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage. Also home of the dedicated regression
  test for this actor family's known self-tripping bug pattern: a
  scope-exclusion term list phrased as a bare noun can accidentally match
  inside the mock advisor's own DEFAULT rationale text for a legitimate,
  allowed proposal, causing the actor to self-block its own happy path."
  (:require [clojure.test :refer [deftest is testing]]
            [timeuseops.advisor :as adv]
            [timeuseops.governor :as gov]
            [timeuseops.store :as store]))

(def household-1 {:household-id "household-1" :name "Tanaka family" :registered? true :verified? true})
(def household-3 {:household-id "household-3" :name "Nakamura family" :registered? true :verified? false})

(defn- clean-proposal [op household-id]
  {:op op :household-id household-id :summary "s" :rationale "routine time-use-survey coordination"
   :cites [household-id] :effect :propose :value {} :confidence 0.85})

(deftest household-unregistered-is-hard
  (testing "no household record at all -> HARD hold"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (clean-proposal :log-time-use-record "unknown-household") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:household-unverified} (map :rule (:violations verdict)))))))

(deftest household-unverified-is-hard
  (testing "household registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"household-3" household-3})
          verdict (gov/check {} nil (clean-proposal :log-time-use-record "household-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:household-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-survey-visit "household-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"household-1" household-1})
          verdict (gov/check {} nil (clean-proposal :finalize-welfare-intervention "household-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest welfare-intervention-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale uses finalization/execution-ACTION language for a welfare intervention is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :flag-welfare-concern "household-1")
                          :rationale "recommend to finalize welfare intervention for this household immediately"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:welfare-intervention-finalization-blocked} (map :rule (:violations verdict)))))))

(deftest custody-removal-content-is-hard
  (testing "a proposal touching custody-removal finalization is HARD-blocked, same as welfare-intervention finalization"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :flag-welfare-concern "household-1")
                          :summary "authorize custody removal for this household's child")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:welfare-intervention-finalization-blocked} (map :rule (:violations verdict)))))))

(deftest protective-order-content-is-hard
  (testing "a proposal touching protective-order issuance finalization is HARD-blocked"
    (let [s (store/mem-store {"household-1" household-1})
          poisoned (assoc (clean-proposal :flag-welfare-concern "household-1")
                          :value {:decision "issue a protective order for this household"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:welfare-intervention-finalization-blocked} (map :rule (:violations verdict)))))))

(deftest legitimate-welfare-concern-is-not-scope-excluded
  (testing "flagging an observed welfare/safety concern (not a finalized intervention) never trips the welfare-intervention-finalization block -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"household-1" household-1})
          concern (assoc (clean-proposal :flag-welfare-concern "household-1")
                         :value {:concern "enumerator observed signs of distress during the household visit"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :welfare-intervention-finalization-blocked (:rule %)) (:violations verdict)))
          "raw observation content (a concern for human triage) is exactly what this op exists to surface"))))

(deftest programme-support-over-threshold-escalates
  (testing "coordinate-programme-support above the cost threshold escalates even when otherwise clean"
    (let [s (store/mem-store {"household-1" household-1})
          proposal (assoc (clean-proposal :coordinate-programme-support "household-1")
                          :value {:cost 5000})
          verdict (gov/check {} nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (true? (:high-stakes? verdict))))))

(deftest programme-support-under-threshold-does-not-escalate-on-cost-alone
  (testing "coordinate-programme-support under the cost threshold is not high-stakes on cost grounds"
    (let [s (store/mem-store {"household-1" household-1})
          proposal (assoc (clean-proposal :coordinate-programme-support "household-1")
                          :value {:cost 40})
          verdict (gov/check {} nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict))))))

(deftest flag-welfare-concern-always-escalates
  (testing ":flag-welfare-concern is always high-stakes/escalate regardless of confidence"
    (let [s (store/mem-store {"household-1" household-1})
          proposal (clean-proposal :flag-welfare-concern "household-1")
          verdict (gov/check {} nil proposal s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (true? (:high-stakes? verdict))))))

;; ----------------------------- the dedicated self-trip regression test -----------------------------

(deftest default-advisor-proposals-never-self-trip-scope-exclusion
  (testing "every default mock-advisor proposal, for every allowed op, on a legitimately registered
            and verified household, must NEVER be HARD-blocked by op-not-allowed or
            welfare-intervention-finalization-blocked -- this is the exact self-tripping bug class
            multiple sibling actors in this fleet independently hit (a bare-noun scope-exclusion
            term matching inside the advisor's own default disclaimer text) and this actor's own
            terms are deliberately phrased as finalization/execution ACTIONS to avoid it"
    (let [db (store/seed-db)
          s (store/mem-store {"household-1" {:household-id "household-1" :registered? true :verified? true}})
          requests {:log-time-use-record       {:op :log-time-use-record :household-id "household-1"
                                                 :patch {:date "2026-07-16" :activity "childcare" :minutes 60}}
                    :schedule-survey-visit      {:op :schedule-survey-visit :household-id "household-1"
                                                 :patch {:enumerator "field-officer-2" :date "2026-07-21"}}
                    :coordinate-programme-support {:op :coordinate-programme-support :household-id "household-1"
                                                   :patch {:material "diary-booklets" :quantity 3 :cost 60}}
                    :flag-welfare-concern       {:op :flag-welfare-concern :household-id "household-1"
                                                 :patch {:concern "possible welfare/safety concern observed" :confidence 0.9}}}]
      (doseq [[op request] requests]
        (let [proposal (adv/infer db request)
              verdict (gov/check request nil proposal s)]
          (is (false? (:hard? verdict))
              (str "op " op " must never HARD self-trip -- violations: " (pr-str (:violations verdict))))
          (is (empty? (filter #(#{:op-not-allowed :welfare-intervention-finalization-blocked} (:rule %))
                              (:violations verdict)))
              (str "op " op " must not self-trip op-not-allowed or welfare-intervention-finalization-blocked"))
          ;; :flag-welfare-concern is the sole always-escalate op; the other three must not be
          ;; high-stakes purely by virtue of their default (under-threshold, non-concern) content.
          (when (not= :flag-welfare-concern op)
            (is (false? (:high-stakes? verdict))
                (str "op " op " must not be high-stakes by default"))))))))
