(ns org.sqids.clojure.generators.init
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.generators.alphabet :as alphabet-generators]
    [org.sqids.clojure.generators.block-list :as block-list-generators]))

(defn ^:private block-list-generator-config
  "Returns shared block-list generator configuration for `alphabet-option`."
  [alphabet-option]
  {:alphabet-option alphabet-option
   :word-characters block-list/block-list-word-characters
   :min-word-length block-list/min-word-length})

(defn ^:private valid-min-length
  "Builds valid min-length values for the public Sqids config contract."
  [min-length-limit]
  (gen/large-integer* {:min 0 :max min-length-limit}))

(defn non-map-input
  "Builds non-map values for invalid Sqids option inputs."
  []
  (gen/one-of
    [(gen/return nil)
     (gen/large-integer* {})
     (gen/double* {})
     (gen/keyword)
     (gen/string-alphanumeric)
     (gen/vector (gen/large-integer* {}))
     (gen/set (gen/large-integer* {}))]))

(defn invalid-alphabet
  "Builds alphabet values that fail alphabet validation."
  []
  (gen/one-of
    [(gen/fmap (partial apply str)
               (gen/vector (gen/elements alphabet-generators/printable-characters) 0 2))
     (gen/fmap (fn [character]
                 (apply str (repeat 3 character)))
               (gen/elements alphabet-generators/printable-characters))
     (gen/fmap (fn [suffix]
                 (str "abc" suffix))
               (gen/elements ["é" "€" "漢"]))]))

(defn invalid-min-length
  "Builds min-length values outside the supported range."
  [min-length-limit]
  (gen/one-of
    [(gen/large-integer* {:max -1})
     (gen/large-integer* {:min (inc min-length-limit)
                          :max (+ min-length-limit 1024)})
     (gen/double* {})]))

(defn invalid-block-list
  "Builds block-list values that fail the public block-list contract."
  []
  (gen/one-of
    [(gen/vector (gen/string-alphanumeric))
     (gen/set (gen/large-integer* {}))
     (gen/map (gen/keyword) (gen/string-alphanumeric))
     (gen/return nil)]))

(defn ^:private invalid-options-map
  "Builds map-shaped invalid Sqids options with one or more bad fields."
  [min-length-limit]
  (gen/one-of
    [(gen/fmap (fn [alphabet-option]
                 {:alphabet alphabet-option})
               (invalid-alphabet))
     (gen/fmap (fn [minimum-length]
                 {:min-length minimum-length})
               (invalid-min-length min-length-limit))
     (gen/fmap (fn [block-list-option]
                 {:block-list block-list-option})
               (invalid-block-list))
     (gen/fmap (fn [[alphabet-option minimum-length block-list-option]]
                 {:alphabet alphabet-option
                  :min-length minimum-length
                  :block-list block-list-option})
               (gen/tuple (invalid-alphabet)
                          (valid-min-length min-length-limit)
                          (block-list-generators/block-list
                            (block-list-generator-config alphabet/default))))
     (gen/fmap (fn [[alphabet-option minimum-length block-list-option]]
                 {:alphabet alphabet-option
                  :min-length minimum-length
                  :block-list block-list-option})
               (gen/tuple (s/gen ::alphabet/alphabet)
                          (invalid-min-length min-length-limit)
                          (block-list-generators/block-list
                            (block-list-generator-config alphabet/default))))
     (gen/fmap (fn [[alphabet-option minimum-length block-list-option]]
                 {:alphabet alphabet-option
                  :min-length minimum-length
                  :block-list block-list-option})
               (gen/tuple (s/gen ::alphabet/alphabet)
                          (valid-min-length min-length-limit)
                          (invalid-block-list)))]))

(defn invalid-options-input
  "Builds invalid option inputs for public option-shaped arguments."
  [min-length-limit]
  (gen/frequency
    [[3 (non-map-input)]
     [7 (invalid-options-map min-length-limit)]]))

(defn ^:private incomplete-sqids
  "Builds initialized-config-shaped maps missing required public Sqids keys."
  [min-length-limit]
  (gen/one-of
    [(gen/return {})
     (gen/fmap (fn [alphabet-option]
                 {:alphabet alphabet-option})
               (s/gen ::alphabet/alphabet))
     (gen/fmap (fn [minimum-length]
                 {:min-length minimum-length})
               (valid-min-length min-length-limit))
     (gen/fmap (fn [block-list-option]
                 {:block-list block-list-option})
               (block-list-generators/block-list
                 (block-list-generator-config alphabet/default)))]))

(defn invalid-sqids-input
  "Builds invalid inputs for initialized Sqids config arguments."
  [min-length-limit]
  (gen/frequency
    [[3 (non-map-input)]
     [2 (incomplete-sqids min-length-limit)]
     [5 (invalid-options-map min-length-limit)]]))

(defn ^:private options-block-list-only
  "Builds options with only an explicit block-list override."
  []
  (gen/fmap (fn [block-list-option]
              {:block-list block-list-option})
            (block-list-generators/block-list
              (block-list-generator-config alphabet/default))))

(defn ^:private options-min-length-and-block-list
  "Builds options with min-length plus a default-alphabet block-list."
  [min-length-limit]
  (gen/fmap (fn [[minimum-length block-list-option]]
              {:min-length minimum-length
               :block-list block-list-option})
            (gen/tuple (valid-min-length min-length-limit)
                       (block-list-generators/block-list
                         (block-list-generator-config alphabet/default)))))

(defn ^:private options-alphabet-and-block-list
  "Builds options whose block-list matches a generated alphabet domain."
  []
  (gen/bind (s/gen ::alphabet/alphabet)
            (fn [alphabet-option]
              (gen/fmap (fn [block-list-option]
                          {:alphabet alphabet-option
                           :block-list block-list-option})
                        (block-list-generators/block-list
                          (block-list-generator-config alphabet-option))))))

(defn ^:private full-options
  "Builds fully specified options with compatible alphabets and block-lists."
  [min-length-limit]
  (gen/bind (s/gen ::alphabet/alphabet)
            (fn [alphabet-option]
              (gen/fmap (fn [[minimum-length block-list-option]]
                          {:alphabet alphabet-option
                           :min-length minimum-length
                           :block-list block-list-option})
                        (gen/tuple (valid-min-length min-length-limit)
                                   (block-list-generators/block-list
                                     (block-list-generator-config alphabet-option)))))))

(defn options
  "Builds Sqids option maps with low default-map noise and aligned block-lists."
  [min-length-limit]
  (gen/frequency
    [[1 (gen/return {})]
     [1 (gen/fmap (fn [alphabet-option]
                    {:alphabet alphabet-option})
                  (s/gen ::alphabet/alphabet))]
     [1 (options-block-list-only)]
     [1 (options-min-length-and-block-list min-length-limit)]
     [2 (options-alphabet-and-block-list)]
     [8 (full-options min-length-limit)]]))

(defn sqids
  "Builds initialized Sqids option maps with all required public keys present."
  [min-length-limit]
  (gen/bind (s/gen ::alphabet/alphabet)
            (fn [alphabet-option]
              (gen/fmap (fn [[minimum-length block-list-option]]
                          {:alphabet alphabet-option
                           :min-length minimum-length
                           :block-list block-list-option})
                        (gen/tuple (valid-min-length min-length-limit)
                                   (block-list-generators/block-list
                                     (block-list-generator-config alphabet-option)))))))

(defn options-input
  "Builds valid and invalid option-shaped API arguments."
  [min-length-limit]
  (gen/frequency
    [[7 (options min-length-limit)]
     [3 (invalid-options-input min-length-limit)]]))

(defn sqids-input
  "Builds valid and invalid initialized Sqids config arguments."
  [min-length-limit]
  (gen/frequency
    [[7 (sqids min-length-limit)]
     [3 (invalid-sqids-input min-length-limit)]]))
