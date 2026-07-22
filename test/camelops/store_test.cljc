(ns camelops.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [camelops.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial facilities"
    (let [facilities {"herd-001" {:id "herd-001" :name "Test Camelid Ranch"}}
          st (store/mem-store {:initial-facilities facilities})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-facility-retrieval
  (testing "Retrieve existing facility"
    (let [facility {:id "herd-001" :name "Test Camelid Ranch"}
          st (store/mem-store {:initial-facilities {"herd-001" facility}})]
      (is (= facility (store/registered-facility st "herd-001")))))

  (testing "Retrieve non-existent facility"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-facility st "no-such-herd")))))

  (testing "nil facility-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-facilities {"herd-001" {:id "herd-001"}}})]
      (is (nil? (store/registered-facility st nil))))))

(deftest add-facility-test
  (testing "Register a new facility"
    (let [st (store/mem-store)
          facility-data {:id "herd-002" :name "New Camelid Ranch"}
          result (store/add-facility st "herd-002" facility-data)]
      (is (= facility-data result))
      (is (= facility-data (store/registered-facility st "herd-002")))))

  (testing "Update an existing facility"
    (let [st (store/mem-store {:initial-facilities {"herd-001" {:id "herd-001"}}})
          updated {:id "herd-001" :name "Renamed Camelid Ranch"}
          result (store/add-facility st "herd-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-facility st "herd-001"))))))

(deftest ledger-contract
  (testing "A fresh store's ledger is empty"
    (let [st (store/mem-store)]
      (is (vector? (store/ledger st)))
      (is (empty? (store/ledger st)))))

  (testing "append-ledger! is append-only and preserves insertion order"
    (let [st (store/mem-store)
          fact-1 {:t :committed :op :log-herd-record}
          fact-2 {:t :governor-hold :op :order-supplies}]
      (is (= fact-1 (store/append-ledger! st fact-1)))
      (store/append-ledger! st fact-2)
      (is (= [fact-1 fact-2] (store/ledger st)))
      (is (= 2 (count (store/ledger st))))))

  (testing "append-ledger! never mutates or removes prior entries"
    (let [st (store/mem-store)]
      (dotimes [n 5]
        (store/append-ledger! st {:t :committed :n n}))
      (is (= (range 5) (map :n (store/ledger st)))))))
