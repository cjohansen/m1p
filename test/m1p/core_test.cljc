(ns m1p.core-test
  (:require [m1p.core :as sut]
            [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]))

(deftest lookup-test
  (testing "Looks up key"
    (is (= (sut/lookup
            (sut/prepare-dictionary
             {:greeting [:fn/str "Hei på deg {{:name}}!"]})
            :greeting
            {:name "Christian"})
           "Hei på deg Christian!")))

  (testing "Looks up namespaced key"
    (is (= (sut/lookup
            (sut/prepare-dictionary
             [#:home
              {:title "Hjemmeside"
               :text "Velkommen hit"}

              #:login
              {:title "Logg inn"
               :text "Skriv inn din epost"}])
            :login/title)
           "Logg inn")))

  (testing "Looks up hiccup with string interpolation"
    (is (= (sut/lookup
            (sut/prepare-dictionary
             {:greeting [:h2 [:fn/str "Hei på deg {{:name}}!"]]})
            :greeting
            {:name "Christian"})
           [:h2 "Hei på deg Christian!"])))

  (testing "Looks up hiccup"
    (is (= (sut/lookup
            (sut/prepare-dictionary
             {:greeting [:h2 "Hei på deg!"]})
            :greeting)
           [:h2 "Hei på deg!"])))

  (testing "Reports missing interpolation key"
    (is (= (sut/lookup
            (sut/prepare-dictionary
             [#:login
              {:help-text [:span {}
                           "Need help? "
                           [:a {:href [:fn/get :url]} "Go here"]]}])
            :login/help-text)
           [:span {}
            "Need help? "
            [:a {:href "[Missing interpolation key :url]"}
             "Go here"]]))))

(deftest interpolate-test
  (testing "Nests dictionary functions and dictionary lookups"
    (is (= (sut/interpolate
            {:dictionaries
             {:i18n
              (sut/prepare-dictionary
               {:title [:h2 "Hei på deg!"]
                :here "her"
                :description [:div "Klikk "
                              [:a {:href [:fn/get :url]}
                               [:fn/get :link-text]]]})}}
            {:title [:i18n :title]
             :description [:i18n :description {:url "/somewhere"
                                               :link-text [:i18n :here]}]})
           {:title [:h2 "Hei på deg!"]
            :description [:div "Klikk " [:a {:href "/somewhere"} "her"]]})))

  (testing "Passes parameters as separate argument"
    (is (= (sut/interpolate
            {:dictionaries
             {:i18n
              (sut/prepare-dictionary
               {:nb {:title [:h2 "Hei på deg!"]
                     :here "her"
                     :description [:div "Klikk "
                                   [:a {:href [:fn/get :url]} [:fn/get :link-text]]]}})}}
            {:title [:fn/get :title]
             :description [:fn/get :description]}
            {:url "/somewhere"
             :link-text [:m1p/k :here]})
           {:title [:h2 "Hei på deg!"]
            :description [:div "Klikk " [:a {:href "/somewhere"} "her"]]}))))

(deftest get-string-placeholders-test
  (testing "Finds all placeholders"
    (is (= (sut/get-string-placeholders "Placeholders are {{:here}}, {{:here}}, and {{:there}}")
           #{["{{:here}}" :here] ["{{:there}}" :there]})))

  (testing "Accepts no placeholders"
    (is (= (sut/get-string-placeholders "No placeholders") #{}))))

(deftest interpolate-string-test
  (testing "Interpolates map value into string"
    (is (= (let [s "Hello {{:greetee}}"]
             (sut/interpolate-string s {:greetee "World"}))
           "Hello World")))

  (testing "Inlines error about missing interpolation by default"
    (is (= (let [s "Hello {{:greetee}}"]
             (sut/interpolate-string s {}))
           "Hello [Missing interpolation key :greetee]"))))

(deftest resolve-val-test
  (testing "Interpolates strings"
    (is (= (sut/resolve-val
            {:dictionary-fns sut/default-dictionary-fns}
            [:fn/str "String with {{:stuff}}"]
            {:stuff "interpolation"})
           "String with interpolation")))

  (testing "Calls dictionary functions"
    (is (= (sut/resolve-val
            {:dictionary-fns {:i18n/custom (constantly "ok")}}
            [:div "Hiccup: " [:i18n/custom]]
            {})
           [:div "Hiccup: " "ok"])))

  (testing "Calls custom functions with opt, params and forms from reference tuple"
    (is (let [args (atom nil)
              opt {:dictionary-fns {:i18n/custom #(reset! args (apply vector %&))}
                   :dictionary {:locale :nb}}]
          (sut/resolve-val opt [:i18n/custom 1 2] {:data "here"})
          (= @args
             [opt {:data "here"} 1 2]))))

  (testing "Interpolates strings from custom functions"
    (is (= (sut/resolve-val
            {:dictionary-fns {:i18n/lower (fn [locale data s]
                                            (str/lower-case s))}}
            [:i18n/lower "Lower cased string"]
            nil)
           "lower cased string"))))

(deftest prepare-dict-val-test
  (testing "Returns plain string untouched"
    (is (= (sut/prepare-dict-val {} "String") "String")))

  (testing "Wraps string in interpolating function"
    (is (= ((sut/prepare-dict-val
             {:dictionary-fns sut/default-dictionary-fns}
             [:fn/str "Hello {{:greetee}}"])
            {:greetee "World"})
           "Hello World")))

  (testing "Returns unprocessable data untouched"
    (is (= (sut/prepare-dict-val {} [:i18n/i "Data"]) [:i18n/i "Data"])))

  (testing "Wraps data in function when it uses dictionary functions"
    (is (= ((sut/prepare-dict-val
             {:dictionary {:locale :en}
              :dictionary-fns {:i18n/i (fn [opt data k]
                                         (get-in data [(-> opt :dictionary :locale) k]))}}
             ["Data: " [:i18n/i :data]])
            {:en {:data 666}
             :nb {:data 999}})
           ["Data: " 666]))))
