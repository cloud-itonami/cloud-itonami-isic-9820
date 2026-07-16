(ns timeuseops.advisor-test
  "Unit tests of `timeuseops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [timeuseops.advisor :as adv]
            [timeuseops.store :as store]))

(def db (store/seed-db))

(deftest propose-time-use-record-shape
  (testing "time-use-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-time-use-record
                           :household-id "household-1"
                           :patch {:date "2026-07-16" :activity "childcare" :minutes 60}})]
      (is (= :log-time-use-record (:op p)))
      (is (= "household-1" (:household-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :household-id)))))

(deftest propose-survey-visit-shape
  (testing "survey-visit proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-survey-visit
                           :household-id "household-2"
                           :patch {:enumerator "field-officer-1" :date "2026-07-20"}})]
      (is (= :schedule-survey-visit (:op p)))
      (is (= "household-2" (:household-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-programme-support-shape
  (testing "programme-support proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-programme-support
                           :household-id "household-1"
                           :patch {:material "diary-booklets" :quantity 2 :cost 40}})]
      (is (= :coordinate-programme-support (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= 40 (get-in p [:value :cost]))))))

(deftest propose-welfare-concern-shape
  (testing "welfare-concern proposal always proposes, never commits"
    (let [p (adv/infer db {:op :flag-welfare-concern
                           :household-id "household-1"
                           :patch {:concern "possible distress observed"}})]
      (is (= :flag-welfare-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-time-use-record :schedule-survey-visit :coordinate-programme-support
                :flag-welfare-concern]]
      (let [p (adv/infer db {:op op :household-id "household-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-time-use-record :schedule-survey-visit :coordinate-programme-support
                :flag-welfare-concern]]
      (let [p (adv/infer db {:op op :household-id "household-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))

(deftest rationale-never-uses-finalization-language
  (testing "no default proposal's rationale/summary ever uses welfare-intervention finalization/execution ACTION language -- the advisor only ever surfaces concerns, never finalizes them"
    (doseq [op [:log-time-use-record :schedule-survey-visit :coordinate-programme-support
                :flag-welfare-concern]]
      (let [p (adv/infer db {:op op :household-id "household-1" :patch {:concern "x"}})
            blob (str (:rationale p) " " (:summary p))]
        (is (not (re-find #"(?i)finalize.*welfare intervention|authorize.*welfare intervention" blob))
            (str "op " op " rationale must not use finalization language: " blob))))))

(deftest out-of-scope-test-hook-poisons-rationale
  (testing "the :out-of-scope? test hook injects finalization language, exercising the governor's block end-to-end (see governor-contract-test)"
    (let [p (adv/infer db {:op :log-time-use-record :household-id "household-1"
                           :out-of-scope? true :patch {}})]
      (is (re-find #"finalize welfare intervention" (:rationale p))))))
