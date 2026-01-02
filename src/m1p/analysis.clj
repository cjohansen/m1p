(ns m1p.analysis
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as edamame]))

(defn get-edamame-opts [{:keys [readers]}]
  (edamame/normalize-opts
   (cond-> {:all true
            :auto-resolve-ns true
            :read-cond :allow
            :location? vector?
            :syntax-quote true
            :deref true
            :fn true
            :var true}
     readers (assoc :readers readers))))

(defn load-namespace [^java.io.File file edamame-opts]
  (try
    (edamame/parse-string-all (slurp file) edamame-opts)
    (catch Exception e
      (throw (ex-info "Unable to load namespace" {:filename (.getPath file)} e)))))

(defn find-interpolation-forms [code {:keys [dictionary-ids]}]
  (->> code
       (tree-seq coll? identity)
       (filter vector?)
       (remove map-entry?)
       (filter (comp (set dictionary-ids) first))
       set))

(defn find-interpolations
  {:arglists '[[{:keys [dictionary-ids readers]} files]]}
  [opt files]
  (let [edamame-opts (get-edamame-opts opt)]
    (->> files
         (filter java.io.File/.isFile)
         (pmap (juxt identity #(find-interpolation-forms (load-namespace % edamame-opts) opt)))
         (remove (comp empty? last)))))

(defn analyze-dictionaries [dictionaries]
  (apply merge (vals dictionaries)))

(defn validate-usage [available-keys ref-tuple]
  (let [k (second ref-tuple)
        size (count ref-tuple)
        dictionary-entry (get available-keys k)]
    (when-let [problem
               (cond
                 (nil? dictionary-entry)
                 :not-found

                 (and (= 2 size) (fn? dictionary-entry))
                 :missing-params

                 (and (= 3 size) (not (fn? dictionary-entry)))
                 :superfluous-params

                 (< 3 size)
                 :too-many-params)]
      (merge (select-keys (meta ref-tuple) [:row :col])
             {:k k
              :problem problem}))))

(defn validate-interpolations [available-keys interpolations]
  (->> interpolations
       (pmap (fn [[^java.io.File file ref-tuples]]
               (for [error (keep #(validate-usage available-keys %) ref-tuples)]
                 (assoc error :file (.getPath file)))))
       (apply concat)
       (sort-by (juxt :file :row :col))))

(defn as-file [file-or-path]
  (if (instance? java.nio.file.Path file-or-path)
    (.toFile ^java.nio.file.Path file-or-path)
    (io/file file-or-path)))

(defn find-unused-keys [dictionaries interpolations]
  (let [used-ks (->> (map second interpolations)
                     (apply concat)
                     (map second)
                     set)]
    (mapcat
     (fn [[dict-k entries]]
       (for [k (->> (keys entries)
                    (remove used-ks))]
         {:problem :unused-key
          :k k
          :dictionary dict-k}))
     dictionaries)))

(defn ^:export find-interpolation-problems
  "Finds problematic interpolation usage. Parses all source files in `files` and
  looks for reference tuples starting with any of the keys in `dictionary-ids`.
  Validates interpolation usage against dictionary entries. Returns a list of
  maps of `{:k :problem :file :row :col}` where

  - `:k` is the interpolated key
  - `:file` the file the form was found in
  - `:row` the line the form was found on
  - `:col` the column the form was found on
  - `:problem` is one of
    - `:not-found`: The key isn't defined in a dictionary
    - `:missing-params`: The entry is parameterized, but no parameter was supplied
    - `:superfluous-params`: The entry is a string value, and a parameter was supplied
    - `:too-many-params`: The form passes more than one parameter"
  {:arglists '[[dictionaries files {:keys [readers dictionary-ids]}]]}
  [dictionaries files opt]
  (let [interpolations (find-interpolations opt (map as-file files))]
    (concat
     (validate-interpolations (analyze-dictionaries dictionaries) interpolations)
     (find-unused-keys dictionaries interpolations))))

(def explanations
  {:not-found "not found in a dictionary"
   :missing-params "is missing parameters used by dictionary entry"
   :superfluous-params "passes parameters to a scalar entry"
   :too-many-params "passes too many parameters"})

(defn format-file-problem [[file problems]]
  (->> (for [{:keys [row k dictionary problem]} (sort-by :row problems)]
         (if (= :unused-key problem)
           (str "  " k " in " dictionary " is never used")
           (str "  " k " on line " row " " (explanations problem))))
       (str/join "\n")
       (str file ":\n")))

(defn format-unused-key-problems [[dictionary problems]]
  (str "Dictionary "dictionary " has unused keys:\n  "
       (str/join ", " (sort (map :k problems)))))

(defn ^:export format-problems
  "Formats the report produced by `find-interpolation-problems` as a multi-line
  string suited for printing."
  [problems]
  (let [file->problems (group-by :file problems)]
    (->> (concat
          (map format-file-problem (dissoc file->problems nil))
          (->> (get file->problems nil)
               (group-by :dictionary)
               (map format-unused-key-problems)))
         (str/join "\n\n"))))

(comment

  ;; Token interpolation forms for the test to find
  [:i18n/k ::key-a]
  [:i18n/k ::key-b {:name "Christian"}]
  [:i18n/k ::key-c {:language "Norwegian"}]
  [:i18n/k ::key-d {:language "Clojure"} :excess-param]
  [:theme/k ::dark]

)
