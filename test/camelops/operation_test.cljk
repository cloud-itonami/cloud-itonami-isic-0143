(ns camelops.operation-test
  "Integration tests for `camelops.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / phase-0-escalate /
  always-escalate-approve / escalate-reject routes. This namespace did
  not exist before: `operation/run-operation` was a plain sequential
  advisor -> governor -> phase-gate function pipeline that never touched
  `kotoba-lang/langgraph` at all (its own namespace docstring said
  StateGraph integration was 'deferred'). These tests prove the compiled
  graph is real and that the audit ledger
  (`camelops.store/append-ledger!`, also new in this fix) is genuinely
  wired into the `:commit`/`:hold`/`:request-approval` nodes."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [camelops.operation :as operation]
            [camelops.store :as store]))

(defn- registered-store []
  (store/mem-store
   {:initial-facilities
    {"herd-001" {:id "herd-001" :name "Test Camelid Ranch" :species "alpaca"}}}))

(defn- exec [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(deftest commit-path-clean-proposal-phase-3
  (testing "a clean, phase-3 herd-record request commits through the real
            compiled graph and appends to the audit ledger"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :log-herd-record :facility-id "herd-001"
                        :count 25 :fiber-yield 3.5}
                       {:actor-id "op-01" :phase :phase-3})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :log-herd-record (:op (first ledger))))
        (is (= "herd-001" (:subject (first ledger))))))))

(deftest ledger-stays-empty-until-real-commit
  (testing "the ledger is empty before a run, and stays empty across the
            advise/govern/decide steps of a run that ends up escalated
            (not yet committed) -- only :commit/:hold/rejection append"
    (let [s (registered-store)
          actor (operation/build s)]
      (is (empty? (store/ledger s)) "ledger starts empty")
      (let [held (exec actor "t-escalate-empty"
                       {:op :flag-animal-health-concern :facility-id "herd-001"
                        :concern "unspecified concern"}
                       {:actor-id "op-01" :phase :phase-3})]
        (is (= :interrupted (:status held)))
        (is (empty? (store/ledger s))
            "an interrupted (awaiting human sign-off) run has NOT committed anything yet")))))

(deftest hard-hold-path-unregistered-facility
  (testing "an unregistered facility is a HARD, permanent governor
            violation -- the real graph routes straight to :hold (no
            interrupt, no human-approval detour) and durably records the
            hold fact"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :log-herd-record :facility-id "unknown-herd" :count 10}
                       {:actor-id "op-01" :phase :phase-3})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :facility-not-registered (:rule %)) (:violations (first ledger))))))))

(deftest hard-hold-path-treatment-blocked
  (testing "direct treatment administration is a HARD, permanent block --
            never routed through human approval"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-treatment"
                       {:op :administer-treatment :facility-id "herd-001"}
                       {:actor-id "op-01" :phase :phase-3})
          state (:state result)]
      (is (= :hold (:disposition state)))
      (is (some #(= :treatment-or-slaughter-blocked (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest hard-hold-path-slaughter-blocked
  (testing "a slaughter/culling proposal is a HARD, permanent block"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-slaughter"
                       {:op :order-slaughter :facility-id "herd-001"}
                       {:actor-id "op-01" :phase :phase-3})]
      (is (= :hold (:disposition (:state result))))
      (is (some #(= :treatment-or-slaughter-blocked (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest hard-hold-path-herd-count-invalid
  (testing "a non-positive herd count is a HARD violation, independently
            re-verified by the governor from the proposal's own :count,
            not trusted from the advisor's confidence"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-count"
                       {:op :log-herd-record :facility-id "herd-001" :count 0}
                       {:actor-id "op-01" :phase :phase-3})]
      (is (= :hold (:disposition (:state result))))
      (is (some #(= :herd-count-invalid (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest hard-hold-path-fiber-yield-invalid
  (testing "a negative fiber yield is a HARD violation"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-fiber"
                       {:op :log-herd-record :facility-id "herd-001"
                        :count 10 :fiber-yield -2.0}
                       {:actor-id "op-01" :phase :phase-3})]
      (is (= :hold (:disposition (:state result))))
      (is (some #(= :fiber-yield-invalid (:rule %))
                (:violations (first (store/ledger s))))))))

(deftest phase-0-escalate-path-clean-proposal
  (testing "phase-0: an otherwise-clean, otherwise-committable proposal is
            forced through human approval (never an autonomous commit,
            NOT a hold -- matches phase/gate's :phase-0 case exactly)"
    (let [s (registered-store)
          actor (operation/build s)
          held (exec actor "t-phase0"
                     {:op :log-herd-record :facility-id "herd-001" :count 25}
                     {:actor-id "op-01" :phase :phase-0})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off"))))

(deftest escalate-then-approve-commits
  (testing ":flag-animal-health-concern ALWAYS escalates -- the real
            graph GENUINELY interrupts (checkpointed) at
            :request-approval; a human veterinarian approve! resumes the
            SAME compiled graph and commits via the graph's own
            :request-approval -> :commit edge, durably appending to the
            ledger"
    (let [s (registered-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :flag-animal-health-concern :facility-id "herd-001"
                      :concern "疾病の可能性"}
                     {:actor-id "op-01" :phase :phase-3})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
      (let [approved (g/run* actor {:approval {:status :approved :by "vet-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-animal-health-concern (:op (first ledger))))
          (is (= "vet-01" (:approved-by (first ledger)))))))))

(deftest escalate-then-reject-holds
  (testing "governor rejection blocks commit: a human rejecting an
            escalated (high-cost supply-order) request routes to :hold
            via the :request-approval node's own decision, and durably
            records the rejection -- not a hand-rolled parallel path"
    (let [s (registered-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :order-supplies :facility-id "herd-001" :cost 1000}
                      {:actor-id "op-01" :phase :phase-3})
          rejected (g/run* actor {:approval {:status :rejected :by "ops-manager-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (= "ops-manager-01" (:by (first ledger))))))))

(deftest supply-order-under-threshold-commits
  (testing "a supply order under its cost threshold auto-commits at
            phase-3 (no escalation needed)"
    (let [s (registered-store)
          actor (operation/build s)
          result (exec actor "t-supply-ok"
                       {:op :order-supplies :facility-id "herd-001" :cost 100}
                       {:actor-id "op-01" :phase :phase-3})]
      (is (= :commit (:disposition (:state result))))
      (is (= 1 (count (store/ledger s)))))))
