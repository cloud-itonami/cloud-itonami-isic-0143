(ns camelops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [camelops.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "feed")]
      (is (= "feed" (:id c)))
      (is (= "飼料" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "feed"                     500
      "veterinary-supply"        500
      "fiber-processing-supply"  500
      "equipment"                1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest species-lookup
  (testing "Lookup valid species"
    (are [id expected-name] (= expected-name (:name (facts/species-by-id id)))
      "dromedary-camel" "ヒトコブラクダ"
      "bactrian-camel"  "フタコブラクダ"
      "llama"           "リャマ"
      "alpaca"          "アルパカ"))

  (testing "Lookup invalid species"
    (is (nil? (facts/species-by-id "unknown")))))

(deftest use-class-lookup
  (testing "Lookup valid use class"
    (are [id expected-name] (= expected-name (:name (facts/use-class-by-id id)))
      "dairy"       "乳用"
      "fiber"       "繊維用"
      "pack-animal" "荷役用"))

  (testing "Lookup invalid use class"
    (is (nil? (facts/use-class-by-id "unknown")))))
