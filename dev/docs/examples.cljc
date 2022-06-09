(ns docs.examples)

;; ex1

(require '[m1p.core :as m1p])

(def dictionary
  {:header/title "Hello, world!"})

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title]]])

;;=> [:div.main [:h1 "Hello, world!"]]

;; ex2

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])

;;=> "Hello, World"

;; ex2-1

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

;; ex2-2

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])

;;=> [:div.main [:h1 "Hello, Internet!"]]

;; ex3

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:span "Hello, "
                   [:strong [:fn/get :greetee]]]}))

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:div.main
  [:h1 [:i18n :header/title {:greetee "Internet"}]]])

;;=> [:div.main [:h1 [:span "Hello, " [:strong "Internet"]]]]

;; ex4

(require '[m1p.core :as m1p])

(def en-dictionary
  (m1p/prepare-dictionary
   [#:home
    {:title "Home page"                          ;; 1
     :text [:fn/str "Welcome {{display-name}}"]} ;; 2

    #:login
    {:title "Log in"
     :help-text [:span {}                        ;; 3
                 "Need help? "
                 [:a {:href [:fn/get :url]}      ;; 4
                  "Go here"]]}]))

;; ex5

(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/lookup {:dictionary dictionary} :songs 0) ;;=> "no songs"
(m1p/lookup {:dictionary dictionary} :songs 1) ;;=> "one song"
(m1p/lookup {:dictionary dictionary} :songs 4) ;;=> "4 songs"

;; ex6

(import '[java.time LocalDateTime]
        '[java.time.format DateTimeFormatter]
        '[java.util Locale])

(defn format-date [opt data pattern local-date]          ;; 1
  (when local-date
    (let [locale (-> opt :dictionary :locale)]           ;; 2
      (-> (DateTimeFormatter/ofPattern pattern)
          (.withLocale (Locale. (name locale)))
          (.format local-date)))))

(def dictionary
  (m1p/prepare-dictionary
   {:locale :en                                          ;; 3
    :updated-at [:fn/date "YYYY-MM-dd" [:fn/get :date]]} ;; 4
   {:dictionary-fns {:fn/date format-date}}))            ;; 5

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 {:text [:i18n :updated-at
         {:date (LocalDateTime/of 2022 6 8 9 37 12)}]})

;;=> {:text "2022-06-08"}
