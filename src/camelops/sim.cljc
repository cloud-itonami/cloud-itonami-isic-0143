(ns camelops.sim
  "Simple simulation/demo runner for the Camelid-Raising Operations
  Coordinator actor. Used to validate that the actor flow compiles and
  basic proposal flow works. Mirrors `cattleops.sim`
  (cloud-itonami-isic-0141)."
  (:require [camelops.operation :as operation]
            [camelops.store :as store]))

(defn demo
  "Run a simple demo scenario: register a facility, propose a herd-record
  log, and check the disposition flow."
  []
  (let [;; Create store with a registered facility
        st (store/mem-store
            {:initial-facilities
             {"herd-001"
              {:id "herd-001"
               :name "Test Camelid Ranch"
               :species "alpaca"}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log a herd record
        request {:op :log-herd-record
                 :facility-id "herd-001"
                 :count 25
                 :fiber-yield 3.5
                 :health-status "healthy"}

        ;; Context with phase 0 (simulation)
        context {:actor-id "camel-ops-01"
                 :role :herder
                 :phase :phase-0}]

    (println "=== Camelid-Raising Operations Coordinator Demo ===")
    (println "Demo facility: herd-001")
    (println "Request: log-herd-record")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 forces human review of all commits)")
    (println)
    (let [result (actor request context)]
      (println "Result disposition:" (:disposition result))
      result)))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
)
