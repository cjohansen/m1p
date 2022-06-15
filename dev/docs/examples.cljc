(ns docs.examples)

;; ex1

(require '[m1p.core :as m1p])

(def dictionary
  {:header/title "Hello, world!"})

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title]]]       ;; 1
 {:dictionaries {:i18n dictionary}}) ;; 2

;;=> [:div.main [:h1 "Hello, world!"]]

;; ex2

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})

;; ex2-1

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

;; ex2-2

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})

;;=> [:div.main [:h1 "Hello, Internet!"]]

;; ex3

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:span "Hello, "
                   [:strong [:fn/get :greetee]]]}))

(m1p/interpolate
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]]
 {:dictionaries {:i18n dictionary}})

;;=> [:div.main [:h1 [:span "Hello, " [:strong "Internet"]]]]

;; ex4

(require '[m1p.core :as m1p])

(def en-dictionary
  (m1p/prepare-dictionary
   [#:home
    {:title "Home page"                           ;; 1
     :text [:fn/str "Welcome {{:display-name}}"]} ;; 2

    #:login
    {:title "Log in"
     :help-text [:span {}                         ;; 3
                 "Need help? "
                 [:a {:href [:fn/get :url]}       ;; 4
                  "Go here"]]}]))

;; ex5

(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "a couple of songs" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/interpolate
 [:ul
  [:li [:i18n :songs 0]]
  [:li [:i18n :songs 1]]
  [:li [:i18n :songs 2]]
  [:li [:i18n :songs 4]]]
 {:dictionaries {:i18n dictionary}})

;;=>
;; [:ul
;;  [:li "no songs"]
;;  [:li "one song"]
;;  [:li "a couple of songs"]
;;  [:li "4 songs"]]

;; ex6

(import '[java.time LocalDateTime]
        '[java.time.format DateTimeFormatter]
        '[java.util Locale])

(defn format-date [opt params pattern local-date]      ;; 1
  (when local-date
    (-> (DateTimeFormatter/ofPattern pattern)
        (.withLocale (Locale. (:locale opt)))          ;; 2
        (.format local-date))))

(def dictionary
  (m1p/prepare-dictionary
   {:updated-at [:fn/str "Last updated "                ;; 3
                 [:fn/date "E MMM d" [:fn/get :date]]]} ;; 4
   {:dictionary-fns {:fn/date format-date}}))           ;; 5

(m1p/interpolate
 {:text [:i18n :updated-at
         {:date (LocalDateTime/of 2022 6 8 9 37 12)}]}
 {:locale "en"                                          ;; 6
  :dictionaries {:i18n dictionary}})

;;=> {:text "Last updated Wed Jun 8"}

;; ex 7

(def dictionary-opts
  {:dictionary-fns {:fn/date format-date
                    :fn/plural pluralize}})

(def dictionaries
  {:en (m1p/prepare-dictionary
        {:title [:fn/str "Hello {{:display-name}}!"]}
        dictionary-opts)

   :nb (m1p/prepare-dictionary
        {:title [:fn/str "Hei {{:display-name}}!"]}
        dictionary-opts)})

(def locale :nb)

(m1p/interpolate
 {:dictionaries {:i18n (get dictionaries locale)}}
 [:i18n :title {:display-name "Meep meep"}])

;; ex8

(def dictionary
  (m1p/prepare-dictionary
   {:updated-at [:fn/str "Last updated "
                 [:fn/date "E MMM d" [:fn/param]]]
    :created-at [:fn/str "Created by {{:creator}} "
                 [:fn/date "E MMM d" [:fn/get :date]]]}
   {:dictionary-fns {:fn/date format-date}}))

(m1p/interpolate
 {:created [:i18n :created-at {:creator "Christian"
                               :date (LocalDateTime/of 2022 6 8 8 37 12)}]
  :updated [:i18n :updated-at (LocalDateTime/of 2022 6 8 9 37 12)]}
 {:locale "en"
  :dictionaries {:i18n dictionary}})

;;=>
;; {:created "Created by Christian Wed Jun 8"
;;  :updated "Last updated Wed Jun 8"}

;; ex9

(def dictionary-opts
  {:dictionary-fns {:fn/date format-date
                    :fn/plural pluralize}})

(def dictionaries
  {:en (m1p/prepare-dictionary
        {:title [:fn/str "Hello {{:display-name}}!"]}
        dictionary-opts)

   :nb (m1p/prepare-dictionary
        {:title [:fn/str "Hei {{:display-name}}!"]}
        dictionary-opts)})

(def locale :nb)

(m1p/interpolate
 [:i18n :title {:display-name "Meep meep"}]
 {:dictionaries {:i18n (get dictionaries locale)}})

;;=> "Hei Meep meep!"

;; ex10

(require '[m1p.core :as m1p]
         '[m1p.validation :as v])

(def dicts
  {:en (m1p/prepare-dictionary
          [#:home
           {:title "Home page"
            :text [:fn/str "Welcome {{:display-name}}"]}

           #:login
           {:title "Log in"
            :help-text [:span {}
                        "Need help? "
                        [:a {:href [:fn/get :url]}
                         "Go here"]]}])

   :nb (m1p/prepare-dictionary
        [#:home
         {:title "Hjemmeside"
          :text "Welcome {{:display-name}}"}

         #:login
         {:title "Logg inn"}])})

(concat
 (v/find-non-kw-keys dicts)
 (v/find-unqualified-keys dicts)
 (v/find-missing-keys dicts)
 (v/find-type-discrepancies dicts)
 (v/find-interpolation-discrepancies dicts)
 (v/find-fn-get-param-discrepancies dicts))

;;=>
;; [{:kind :missing-key
;;   :dictionary :nb
;;   :key :login/help-text}
;;  {:kind :interpolation-discrepancy
;;   :key :home/text
;;   :dictionaries {:en #{["{{:display-name}}" :display-name]}
;;                  :nb #{}}}]

;; ex11

(->> (concat
      (v/find-non-kw-keys dicts)
      (v/find-unqualified-keys dicts)
      (v/find-missing-keys dicts)
      (v/find-type-discrepancies dicts)
      (v/find-interpolation-discrepancies dicts)
      (v/find-fn-get-param-discrepancies dicts))
     (v/print-report dicts))

;; Problems in :nb
;;   Missing keys:
;;     :login/help-text
;;
;; Interpolation discrepancies
;;   :home/text
;;     :en #{["{{:display-name}}" :display-name]}
;;     :nb #{}
