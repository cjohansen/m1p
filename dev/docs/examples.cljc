(ns docs.examples)

;; ex1

(require '[m1p.core :as m1p])

(def dictionary
  (m1p/prepare-dictionary
   {:greeting [:fn/str "Hello, {{:greetee}}"]}))

;; ex2

(m1p/lookup dictionary :greeting {:greetee "World"})

;;=> "Hello, World"

;; ex3

(def dictionary
  (m1p/prepare-dictionary
   {:text [:span "Hello, " [:bold [:fn/str "{{:greetee}}"]]]}))

(m1p/lookup dictionary :text {:greetee "World"})

;;=> [:span "Hello, " [:bold "World"]]

;; ex4

(def dictionary
  (m1p/prepare-dictionary
   {:text [:span "Hello, " [:bold [:fn/get :greetee]]]}))

(m1p/lookup dictionary :text {:greetee "World"})

;;=> [:span "Hello, " [:bold "World"]]

;; ex5

(def dictionaries
  {:en
   (m1p/prepare-dictionary
    {:login/title [:fn/str "Log in to {{:site}}"]
     :login/text "Enter your email and we'll send you a code for login"
     :login/button-text "Gimme"
     :login/email-placeholder "Email"})})

(def page-data
  (let [locale :en
        dict (get dictionaries locale)]
    {:title (m1p/lookup dict :login/title {:site "My site"})
     :text (m1p/lookup dict :login/text)
     :form {:button {:disabled? true
                     :spinner? false
                     :text (m1p/lookup dict :login/button-text)}
            :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
                      :placeholder (m1p/lookup dict :login/email-placeholder)}]}}))

;; ex6

(def page-data
  {:title [:i18n :login/title {:site "My site"}] ;; 1
   :text [:i18n :login/text]
   :form {:button {:disabled? true
                   :spinner? false
                   :text [:i18n :login/button-text]}
          :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
                    :placeholder [:i18n :login/email-placeholder]}]}})

(m1p/interpolate
 {:dictionaries                                  ;; 2
  {:i18n                                         ;; 3
   (:en dictionaries)}}
 page-data)

;;=>
;; {:title "Log in to My site"
;;  :text "Enter your email and we'll send you a code for login"
;;  :form {:button {:disabled? true
;;                  :spinner? false
;;                  :text "Gimme"}
;;         :inputs [{:on-input [[:assoc-in [:transient :email] :event/target.value]]
;;                   :placeholder "Email"}]}}

;; ex7

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

;; ex8

(require '[m1p.core :as m1p])

(defn pluralize [opt n & plurals]
  (-> (nth plurals (min (if (number? n) n 0) (dec (count plurals))))
      (m1p/interpolate-string {:n n} opt)))

(def dictionary
  (m1p/prepare-dictionary
   {:songs [:fn/plural "no songs" "one song" "{{:n}} songs"]}
   {:dictionary-fns {:fn/plural pluralize}}))

(m1p/lookup dictionary :songs 0) ;;=> "no songs"
(m1p/lookup dictionary :songs 1) ;;=> "one song"
(m1p/lookup dictionary :songs 4) ;;=> "4 songs"

;; ex8-1

[:i18n :songs 0]

;; ex9

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
