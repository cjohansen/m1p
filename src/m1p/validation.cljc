(ns m1p.validation
  "Functions to validate dictionary parity. Parity is tested on a map of
  dictionaries keyed by \"flavor\" (e.g. locale for i18n dictionaries, theme for
  theme dictionaries, etc):

  ```clj
  (def dicts
    {:en {:key1 \"...\"
          :key2 \"...\"}
     :nb {:key1 \"...\"
          :key2 \"...\"}})
  ```

  Validation functions return a list of maps of `:dictionary`, `:key`, and a
  `:kind` of problem. Some validators include additional keys to contextualize
  the problem."
  (:require [m1p.core :as m1p]))

(defn find-non-kw-keys
  "Return a list of all keys across all dictionaries that are not keywords."
  [dicts]
  (mapcat (fn [[d dict]]
            (->> (keys dict)
                 (remove keyword?)
                 (map (fn [k]
                        {:kind :non-kw-key
                         :dictionary d
                         :key k}))))
          dicts))

(defn find-unqualified-keys
  "Return a list of all keys across all dictionaries that don't have a namespace."
  [dicts]
  (mapcat (fn [[d dict]]
            (->> (keys dict)
                 (filter keyword?)
                 (remove namespace)
                 (map (fn [k]
                        {:kind :unqualified-key
                         :dictionary d
                         :key k}))))
          dicts))

(defn find-missing-keys
  "Finds all keys used across all dictionaries and returns a list of keys missing
  from individual dictionaries."
  [dicts]
  (let [ks (set (mapcat keys (vals dicts)))]
    (->> dicts
         (mapcat (fn [[d dict]]
                   (->> (remove (set (keys dict)) ks)
                        (map (fn [k]
                               {:kind :missing-key
                                :dictionary d
                                :key k}))))))))

(defn dfn?
  "Returns `true` if `x` is a reference to the dictionary function `f`, e.g.:
   `(dfn? :fn/str [:fn/str \"Hello\"]) ;;=> true`"
  [f x]
  (and (vector? x) (= f (first x))))

(defn get-type
  "Returns `:string` if the value is a string or a reference to `:fn/str`,
  otherwise the value's `type`."
  [x]
  (cond
    (or (string? x) (dfn? :fn/str x)) :string
    :else (type x)))

(defn map-dictionary-vals
  "Maps of the values of each dictionary in `dicts` and returns the result, which
  is suitable for use with `find-val-discrepancies`."
  [f dicts]
  (->> dicts
       (map (fn [[d dict]]
              [d (->> (map (fn [[k v]]
                             [k (f v)])
                           dict)
                      (into {}))]))
       (into {})))

(defn find-val-discrepancies
  "Returns a list of all keys across all dictionaries that have different values.
  Before calling this with `dicts`, pass it through a function that replace
  individual dictionary values with some symbolic type, e.g. call
  `map-dictionary-vals` with `get-type` or similar. `kind` is the `:kind` to
  assoc on the detected problems."
  [kind dicts]
  (->> (set (mapcat keys (vals dicts)))
       (map (fn [k]
              (->> dicts
                   (remove #(not (contains? (second %) k)))
                   (map (fn [[d dict]]
                          {:dictionary d
                           :key k
                           :data (get dict k)})))))
       (filter #(< 1 (count (set (map :data %)))))
       (mapcat #(map (fn [x] (assoc x :kind kind)) %))))

(defn find-type-discrepancies
  "Returns a list of all keys whose type is not the same across all dictionaries.
  The list will include one entry per dictionary for each key with type
  discrepancies."
  [dicts]
  (->> (map-dictionary-vals get-type dicts)
       (find-val-discrepancies :type-discrepancy)))

(defn find-str-interpolations
  "Recursively find all `:fn/str` references in `x`"
  [x]
  (->> (tree-seq coll? identity x)
       (filter #(dfn? :fn/str %))
       (mapcat (fn [v]
                 (->> (drop 1 v)
                      (filter string?)
                      (mapcat m1p/get-string-placeholders))))
       set))

(defn find-interpolation-discrepancies
  "Returns a list of all keys whose values use a different set of string
  interpolations."
  [dicts]
  (->> (map-dictionary-vals find-str-interpolations dicts)
       (find-val-discrepancies :interpolation-discrepancy)))

(defn find-fn-get-params
  "Recursively find all arguments used with `:fn/get` in `x`."
  [x]
  (->> (tree-seq coll? identity x)
       (filter #(dfn? :fn/get %))
       (map second)
       set))

(defn find-fn-get-param-discrepancies
  "Returns a list of all keys whose values use a different set of parameters with
  `:fn/get`."
  [dicts]
  (->> (map-dictionary-vals find-fn-get-params dicts)
       (find-val-discrepancies :fn-get-param-discrepancy)))
