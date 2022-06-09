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
   {:songs [:fn/plural "no songs" "one song" "a couple of songs" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/interpolate
 {:dictionaries {:i18n dictionary}}
 [:ul
  [:li [:i18n :songs 0]]
  [:li [:i18n :songs 1]]
  [:li [:i18n :songs 2]]
  [:li [:i18n :songs 4]]])

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
    (let [locale (:locale opt)]                        ;; 2
      (-> (DateTimeFormatter/ofPattern pattern)
          (.withLocale (Locale. locale))
          (.format local-date)))))

(def dictionary
  (m1p/prepare-dictionary
   {:updated-at [:fn/str "Last updated "                ;; 3
                 [:fn/date "E MMM d" [:fn/get :date]]]} ;; 4
   {:dictionary-fns {:fn/date format-date}}))           ;; 5

(m1p/interpolate
 {:locale "en"                                          ;; 6
  :dictionaries {:i18n dictionary}}
 {:text [:i18n :updated-at
         {:date (LocalDateTime/of 2022 6 8 9 37 12)}]})

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
