(ns m1p.core
  (:require #?(:cljs [cljs.reader :as reader])
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn get-string-placeholders
  "Find all interpolation placeholders in the string. An interpolation placeholder
  is a double bracket around an identifier, e.g. `\"Hello {{greetee}}\"`

  Returns a set of tuples of the exact string placeholder and the placeholder as
  a keyword, e.g.:

  ```clj
  #{[\"{{:greetee}}\" :greetee]}
  ```"
  [s]
  (->> (re-seq #"\{\{([^\}]+)\}\}" s)
       (map (fn [[_ k]]
              [(str "{{" k "}}") #?(:clj (read-string k)
                                    :cljs (reader/read-string k))]))
       set))

(defn interpolate-string [s interpolations & [opt]]
  (->> (get-string-placeholders s)
       (reduce (fn [s [ph k]]
                 (str/replace s ph
                  (if (contains? interpolations k)
                    (str (get interpolations k))
                    ((or (:fn.str/on-missing-interpolation opt)
                         (constantly (str "[Missing interpolation key " k "]")))
                     opt interpolations k))))
               s)))

(defn resolve-val
  "Resolve the val `v` by applying matching `dictionary-fns`. Dictionary functions
  are called when `v` contains a vector where the first value is a key in
  `dictionary-fns`. The function will then be called with `opt`, `params`, and
  the rest of the values from the vector. See m1p's Readme for more about
  dictionary functions."
  [{:keys [dictionary-fns] :as opt} v lookup-opt data]
  (walk/postwalk
   (fn [x]
     (if (and (vector? x)
              (contains? dictionary-fns (first x)))
       (try
         (apply (get dictionary-fns (first x)) lookup-opt data (rest x))
         (catch #?(:clj Exception :cljs :default) e
           (let [lookup-key (::lookup-key opt)]
             (throw (ex-info (str "Exception when resolving val for " lookup-key)
                             {:fn (first x)
                              :lookup-key lookup-key
                              :data data}
                             e)))))
       x))
   v))

(defn prepare-dict-val
  "Prepares dictionary value `v`. If it contains data that matches any
  `:dictionary-fns` in `opt`, `prepare-dict-val` returns a partially applied
  `resolve-val`, otherwise returns `v` untouched."
  [opt v]
  (let [syms (tree-seq coll? identity v)]
    (if (some (set (keys (:dictionary-fns opt))) syms)
      (with-meta (partial resolve-val opt v) {::value v})
      v)))

(def default-dictionary-fns
  {:fn/str (fn [opt params & ss]
             (->> ss
                  (map #(interpolate-string % params opt))
                  str/join))

   :fn/get (fn [opt params k]
             (if (contains? params k)
               (get params k)
               ((or (:fn.get/on-missing-key opt)
                    (constantly (str "[Missing key " k "]")))
                opt params k)))

   :fn/param (fn [opt param] param)})

(defn prepare-dictionary
  "Prepares dictionary for use with `lookup` and `interpolate`. `dictionary` is
  either a map or a collection of maps. If multiple maps are passed, they are
  merged and all the values in the resulting map are passed to
  `prepare-dict-val`. Returns the prpeared dictionary map.

  `opt` is an optional map of options:

  - `:dictionary-fns`
    Functions to apply to resolved data. When resolved data contains a vector
    with a keyword in the first position matching a key in this map, the vector
    is replaced by calling the associated function with `opt`, the passed in
    `params`, and remaining forms from the original vector."
  [dictionary & [opt]]
  (let [options (update opt :dictionary-fns #(merge default-dictionary-fns %))]
    (->> (if (sequential? dictionary)
           (apply merge dictionary)
           dictionary)
         (map (fn [[k v]]
                [k (prepare-dict-val (assoc options ::lookup-key k) v)]))
         (into {}))))

(defn lookup
  "Lookup `k` in `dictionary`, passing `params` to dictionary functions. `opt` is
  a map of options to pass to dictionary functions."
  ([opt dictionary k]
   (lookup opt dictionary k nil))
  ([opt dictionary k data]
   (if (not (contains? dictionary k))
     ((or (:on-missing-dictionary-key opt)
          (constantly [::error "Missing dictionary key" k data]))
      opt data k)
     (let [v (get dictionary k)]
       (if (fn? v)
         (v opt data)
         v)))))

(defn interpolate
  "Walk `data` and replace references to dictionary keys with the result of
  calling `lookup`. A dictionary key reference is a tuple on the form:

  ```clj
  [dictionary-k k & params]
  ```

  Where `dictionary-k` is a key in `:dictionaries`, e.g. `:i18n`, `k` is a key
  in said dictionary, and `params` are optional arbitrary data. `lookup` will be
  called with `opt` (as passed to `interpolate`), `params` and `k`."
  ([data {:keys [dictionaries] :as opt}]
   (let [opt (dissoc opt :dictionaries)]
     (walk/postwalk
      (fn [x]
        (let [k (when (vector? x) (first x))]
          (if (and k (contains? dictionaries k))
            (apply lookup opt (get dictionaries k) (rest x))
            x)))
      data))))
