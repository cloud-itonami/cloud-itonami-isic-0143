(ns camelops.sim
  "Demo driver -- `clojure -M:run` / `clojure -M:dev:run`. Drives the
  REAL compiled `langgraph-clj` `StateGraph` (`camelops.operation/
  build`) end-to-end through a phase-3 auto-commit, a phase-0
  escalate-then-approve, an always-escalate health-concern flag, a
  supply-order high-cost escalation, and the HARD-block scenarios
  (unregistered facility, treatment/slaughter blocked, non-positive herd
  count, negative fiber yield), then prints the resulting audit ledger.
  Mirrors `transportops.sim` (cloud-itonami-isic-869)."
  (:require [langgraph.graph :as g]
            [camelops.store :as store]
            [camelops.operation :as operation]
            [camelops.governor :as governor]))

(defn scenario [title]
  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println (str "Scenario: " title))
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid by]
  (g/run* actor {:approval {:status :rejected :by by}}
          {:thread-id tid :resume? true}))

(defn- seeded-store []
  (store/mem-store
   {:initial-facilities
    {"herd-001" {:id "herd-001" :name "Test Camelid Ranch" :species "alpaca"}}}))

(defn demo
  "Run the compiled StateGraph through a phase-3 auto-commit path, a
  phase-0 escalate-then-approve, an always-escalating health-concern
  flag, a high-cost supply-order escalation, and HARD-block scenarios;
  print each result and the final audit ledger."
  []
  (println "Camelid-Raising Operations Coordinator Actor - Demo")

  (scenario "Phase 3: Auto-commit herd-record logging")
  (let [s (seeded-store)
        actor (operation/build s)
        result (exec-op actor "t1"
                        {:op :log-herd-record :facility-id "herd-001"
                         :count 25 :fiber-yield 3.5}
                        {:actor-id "camel-ops-01" :role :herder :phase :phase-3})]
    (println (:state result))
    (println "Disposition:" (:disposition (:state result))))

  (scenario "Phase 0: Clean proposal escalates (no autonomous commits during simulation)")
  (let [s (seeded-store)
        actor (operation/build s)
        held (exec-op actor "t2"
                      {:op :log-herd-record :facility-id "herd-001" :count 25}
                      {:actor-id "camel-ops-01" :role :herder :phase :phase-0})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- herder approves --")
    (let [approved (approve! actor "t2" "herder-01")]
      (println (:state approved))
      (println "Disposition:" (:disposition (:state approved)))))

  (scenario "Always-escalating: animal health/welfare concern (ALWAYS pauses at :request-approval)")
  (let [s (seeded-store)
        actor (operation/build s)
        held (exec-op actor "t3"
                      {:op :flag-animal-health-concern :facility-id "herd-001"
                       :concern "疾病の可能性"}
                      {:actor-id "camel-ops-01" :role :herder :phase :phase-3})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- veterinarian approves --")
    (let [approved (approve! actor "t3" "vet-01")]
      (println (:state approved))
      (println "Disposition:" (:disposition (:state approved)))))

  (scenario "Escalate-then-reject: high-cost supply order")
  (let [s (seeded-store)
        actor (operation/build s)
        held (exec-op actor "t4"
                      {:op :order-supplies :facility-id "herd-001" :cost 1000}
                      {:actor-id "camel-ops-01" :role :herder :phase :phase-3})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- ops-manager rejects --")
    (let [rejected (reject! actor "t4" "ops-manager-01")]
      (println (:state rejected))
      (println "Disposition:" (:disposition (:state rejected)))))

  (scenario "HARD-block: Unregistered facility")
  (let [s (seeded-store)
        actor (operation/build s)
        result (exec-op actor "t5"
                        {:op :log-herd-record :facility-id "unknown-herd" :count 10}
                        {:actor-id "camel-ops-01" :phase :phase-3})]
    (println "Disposition:" (:disposition (:state result))
             "Audit:" (:audit (:state result))))

  (scenario "HARD-block: Slaughter/culling decision is permanently blocked")
  (let [s (seeded-store)
        actor (operation/build s)
        result (exec-op actor "t6"
                        {:op :order-slaughter :facility-id "herd-001"}
                        {:actor-id "camel-ops-01" :phase :phase-3})]
    (println "Disposition:" (:disposition (:state result))
             "Audit:" (:audit (:state result))))

  (scenario "HARD-block: Non-positive herd count (governor check, pre-graph)")
  (let [s (seeded-store)
        proposal {:op :log-herd-record :effect :propose :count 0}
        verdict (governor/check {:facility-id "herd-001"} nil proposal s)]
    (println "Violations:" (map :rule (:violations verdict))))

  (scenario "HARD-block: Negative fiber yield (governor check, pre-graph)")
  (let [s (seeded-store)
        proposal {:op :log-herd-record :effect :propose :count 5 :fiber-yield -1.5}
        verdict (governor/check {:facility-id "herd-001"} nil proposal s)]
    (println "Violations:" (map :rule (:violations verdict))))

  (println "\n" "=" "=" "=" "=" "=" "=" "=" "=" "=" "=")
  (println "Demo completed successfully")
  (println "=" "=" "=" "=" "=" "=" "=" "=" "=" "="))

(defn -main [& _args]
  (demo))

(comment
  (demo))
