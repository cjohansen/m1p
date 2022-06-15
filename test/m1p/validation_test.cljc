(ns m1p.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [m1p.core :as m1p]
            [m1p.validation :as sut]))

(deftest find-non-kw-keys-test
  (is (= (sut/find-non-kw-keys
          {:en {"string" 1
                "string2" 2
                :kw 3}
           :nb {"other" 2
                :kw 2}})
         [{:kind :non-kw-key, :dictionary :en, :key "string"}
          {:kind :non-kw-key, :dictionary :en, :key "string2"}
          {:kind :non-kw-key, :dictionary :nb, :key "other"}])))

(deftest find-unqualified-keys-test
  (is (= (sut/find-unqualified-keys
          {:en {:qualified/key "Ok"
                :key2 "Not ok"
                :key3 "Also not ok"}
           :nb {:qualified/key "Ok"
                :key2 "Not ok"
                :key4 "Also not ok"}})
         [{:kind :unqualified-key, :dictionary :en, :key :key2}
          {:kind :unqualified-key, :dictionary :en, :key :key3}
          {:kind :unqualified-key, :dictionary :nb, :key :key2}
          {:kind :unqualified-key, :dictionary :nb, :key :key4}])))

(deftest find-missing-keys-test
  (is (= (sut/find-missing-keys
          {:en {:key1 "One"
                :key2 "Two"
                :key4 "Four"}
           :nb {:key1 "One"
                :key3 "Three"}})
         [{:kind :missing-key, :dictionary :en, :key :key3}
          {:kind :missing-key, :dictionary :nb, :key :key2}
          {:kind :missing-key, :dictionary :nb, :key :key4}])))

(deftest map-dictionary-vals-test
  (testing "Finds all types of keys"
    (is (= (sut/map-dictionary-vals
            sut/get-type
            {:en {:key1 "One"
                  :key2 [:fn/str "Two"]
                  :key4 "Four"}
             :nb {:key1 "One"
                  :key3 "Three"}})
           {:en {:key1 :string
                 :key2 :string
                 :key4 :string}
            :nb {:key1 :string
                 :key3 :string}})))

  (testing "Finds type of keys in prepared dictionary"
    (is (= (sut/map-dictionary-vals
            sut/get-type
            {:en (m1p/prepare-dictionary
                  {:key1 "One"
                   :key2 [:fn/str "Two"]
                   :key4 "Four"})})
           {:en {:key1 :string
                 :key2 :string
                 :key4 :string}})))

  (testing "Finds all interpolations"
    (is (= (sut/map-dictionary-vals
            sut/find-string-interpolations
            {:en {:key1 "One"
                  :key2 [:div
                         "Hello "
                         [:bold [:fn/str "{{:number}}"]]
                         [:fn/str "How" " are " "{{:who}}"]]}
             :nb {:key1 "One"
                  :key2 [:fn/str "{{:number}}"]}})
           {:en {:key1 #{}
                 :key2 #{:who :number}}
            :nb {:key1 #{}
                 :key2 #{:number}}})))

  (testing "Finds all interpolations in prepared dictionaries"
    (is (= (sut/map-dictionary-vals
            sut/find-string-interpolations
            {:en (m1p/prepare-dictionary
                  {:key1 "One"
                   :key2 [:div
                          "Hello "
                          [:bold [:fn/str "{{:number}}"]]
                          [:fn/str "How" " are " "{{:who}}"]]})})
           {:en {:key1 #{}
                 :key2 #{:who :number}}}))))

(deftest find-type-discrepancies-test
  (testing "Finds type discrepancies"
    (is (= (sut/find-type-discrepancies
            {:en {:key1 "String"
                  :key2 2
                  :key3 [:div]}
             :nb {:key1 [:fn/str "Ok"]
                  :key2 "String"
                  :key3 '()}})
           [{:kind :type-discrepancy
             :key :key3
             :dictionaries {:en :vector, :nb :list}}
            {:kind :type-discrepancy
             :key :key2
             :dictionaries {:en :number :nb :string}}])))

  (testing "Finds type discrepancies in prepared dictionaries"
    (is (= (sut/find-type-discrepancies
            {:en (m1p/prepare-dictionary
                  {:key1 "String"
                   :key2 2
                   :key3 [:div]})
             :nb (m1p/prepare-dictionary
                  {:key1 [:fn/str "Ok"]
                   :key2 "String"
                   :key3 '()})})
           [{:kind :type-discrepancy
             :key :key3
             :dictionaries {:en :vector, :nb :list}}
            {:kind :type-discrepancy
             :key :key2
             :dictionaries {:en :number :nb :string}}]))))

(deftest find-misplaced-interpolation-test
  (testing "Finds interpolation outside fn/str"
    (is (= (sut/find-misplaced-interpolations
            {:en (m1p/prepare-dictionary
                  {:key1 "Hello {{:greetee}}"})})
           [{:kind :misplaced-interpolation-syntax
             :dictionary :en
             :key :key1}])))

  (testing "Finds interpolation nested inside fn/str"
    (is (= (sut/find-misplaced-interpolations
            {:en (m1p/prepare-dictionary
                  {:key1 [:fn/str "Hello " [:strong "{{:greetee}}"]]})})
           [{:kind :misplaced-interpolation-syntax
             :dictionary :en
             :key :key1}])))

  (testing "Ignores correctly placed interpolation syntax"
    (is (= (sut/find-misplaced-interpolations
            {:en (m1p/prepare-dictionary
                  {:key1 [:fn/str "Hello {{:greetee}}"]})})
           []))))
