(ns m1p.analysis-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [m1p.analysis :as analysis]))

(deftest load-namespace-test
  (testing "Loads code in namespace"
    (is (= (-> (io/file "src/m1p/analysis.clj")
               (analysis/load-namespace (analysis/get-edamame-opts {}))
               first
               second)
           'm1p.analysis))))

(deftest find-interpolation-forms-test
  (testing "Finds all interpolation forms"
    (is (= (-> (io/file "src/m1p/analysis.clj")
               (analysis/load-namespace (analysis/get-edamame-opts {}))
               (analysis/find-interpolation-forms {:dictionary-ids #{:i18n/k :theme/k}}))
           #{[:i18n/k :m1p.analysis/key-a]
             [:i18n/k :m1p.analysis/key-b {:name "Christian"}]
             [:i18n/k :m1p.analysis/key-c {:language "Norwegian"}]
             [:i18n/k :m1p.analysis/key-d {:language "Clojure"} :excess-param]
             [:theme/k :m1p.analysis/dark]})))

  (testing "Finds speciufic interpolation forms"
    (is (= (-> (io/file "src/m1p/analysis.clj")
               (analysis/load-namespace (analysis/get-edamame-opts {}))
               (analysis/find-interpolation-forms {:dictionary-ids #{:theme/k}}))
           #{[:theme/k :m1p.analysis/dark]}))))

(deftest find-interpolation-problems-test
  (testing "Uses dictionary entries to validate usages"
    (is (= (analysis/find-interpolation-problems
            {:en #:m1p.analysis{:key-a "A"
                                :key-b (fn [])
                                :key-c "C"
                                :key-d "D"}}
            [(io/file "src/m1p/analysis.clj")]
            {:dictionary-ids #{:i18n/k}})
           [{:file "src/m1p/analysis.clj"
             :row 149
             :col 3
             :k :m1p.analysis/key-c
             :problem :superfluous-params}
            {:file "src/m1p/analysis.clj"
             :row 150
             :col 3
             :k :m1p.analysis/key-d
             :problem :too-many-params}])))

  (testing "Finds unused keys"
    (is (= (analysis/find-interpolation-problems
            {:en #:m1p.analysis{:dark "Dark!"
                                :light "Light!"}}
            [(io/file "src/m1p/analysis.clj")]
            {:dictionary-ids #{:theme/k}})
           [{:dictionary :en
             :k :m1p.analysis/light
             :problem :unused-key}]))))

(deftest format-interpolation-report-test
  (testing "Makes nice report"
    (is (= (analysis/format-problems
            (analysis/find-interpolation-problems
             {:en #:m1p.analysis{:key-a "A"
                                 :key-b (fn [])
                                 :key-c "C"
                                 :key-d "D"
                                 :dark "Dark"
                                 :light "Light"}}
             [(io/file "src/m1p/analysis.clj")]
             {:dictionary-ids #{:i18n/k :theme/k}}))
           (str "src/m1p/analysis.clj:\n"
                "  :m1p.analysis/key-c on line 149 passes parameters to a scalar entry\n"
                "  :m1p.analysis/key-d on line 150 passes too many parameters\n\n"
                "Dictionary :en has unused keys:\n"
                "  :m1p.analysis/light")))))
