(ns timeuseops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean time-use-record logging
  request through intake -> advise -> govern -> decide -> approval -> commit
  at phase 1 (assisted-logging, always approval), then re-runs the same op
  at phase 3 (supervised-auto, clean + high confidence -> auto-commit),
  then a survey-visit-scheduling request, a programme-support coordination
  request under the cost threshold (auto-commits), then one over the cost
  threshold (escalates), then a welfare-concern flag (ALWAYS escalates, at
  any phase -- approve, then commit), then HARD-hold scenarios: an
  unregistered household, a household registered but not yet verified, a
  proposal whose own `:effect` is not `:propose`, and a proposal that has
  drifted into finalizing a welfare-intervention decision."
  (:require [langgraph.graph :as g]
            [timeuseops.advisor :as advisor]
            [timeuseops.store :as store]
            [timeuseops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "survey-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :survey-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :survey-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-time-use-record household-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-time-use-record :household-id "household-1"
                                  :patch {:date "2026-07-16" :activity "childcare" :minutes 90}} coordinator-phase-1)]
      (println r)
      (println "-- human survey coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-time-use-record household-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-time-use-record :household-id "household-1"
                                  :patch {:date "2026-07-16" :activity "meal preparation" :minutes 45}} coordinator-phase-3))

    (println "\n== schedule-survey-visit household-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-survey-visit :household-id "household-1"
                                  :patch {:enumerator "field-officer-3" :date "2026-07-20" :time "10:00"}} coordinator-phase-3))

    (println "\n== coordinate-programme-support household-1, under threshold (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-programme-support :household-id "household-1"
                                  :patch {:material "diary-booklets" :quantity 2 :cost 40}} coordinator-phase-3))

    (println "\n== coordinate-programme-support household-1, OVER threshold (phase 3, still escalates) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-programme-support :household-id "household-1"
                                 :patch {:material "enumerator-training" :quantity 1 :cost 850}} coordinator-phase-3)]
      (println r)
      (println "-- human survey coordinator approves the budget --")
      (println (approve! actor "t5")))

    (println "\n== flag-welfare-concern household-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-welfare-concern :household-id "household-1"
                                 :patch {:concern "enumerator observed signs of distress during visit" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human survey coordinator reviews & approves human-triage routing --")
      (println (approve! actor "t6")))

    (println "\n== log-time-use-record household-99 (unregistered household -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-time-use-record :household-id "household-99"
                                  :patch {:date "2026-07-16" :activity "unknown"}} coordinator-phase-3))

    (println "\n== log-time-use-record household-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-time-use-record :household-id "household-3"
                                  :patch {:date "2026-07-16" :activity "cleaning"}} coordinator-phase-3))

    (println "\n== schedule-survey-visit household-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t9" {:op :schedule-survey-visit :household-id "household-1"
                                           :patch {:enumerator "field-officer-1" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-time-use-record household-1, advisor drifts into finalizing a welfare intervention -> HARD hold, permanent ==")
    (println (exec-op actor "t10" {:op :log-time-use-record :household-id "household-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
