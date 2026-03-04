(ns org.sqids.clojure.generators.encoding
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [org.sqids.clojure.generators.init :as init-generators]
    [org.sqids.clojure.platform :as platform]))

(def nat-ints-spec-key
  "Spec key used for generated natural integer collections."
  :org.sqids.clojure.encoding/nat-ints)

(def init-sqids-spec-key
  "Spec key used for generated initialized Sqids configs."
  :org.sqids.clojure.init/sqids)

(defn nat-int
  "Builds natural integers with weighted boundary coverage for encode specs."
  []
  (gen/frequency
    [[7 (gen/large-integer* {:min 0 :max platform/nat-int-random-max})]
     [3 (gen/elements platform/nat-int-edge-values)]]))

(defn ^:private invalid-number-value
  "Builds values that fail the natural integer contract."
  []
  (gen/one-of
    [(gen/large-integer* {:max -1})
     (gen/double* {})
     (gen/return nil)
     (gen/string-alphanumeric)
     (gen/keyword)]))

(defn invalid-numbers-input
  "Builds invalid values for encode number collections."
  []
  (gen/one-of
    [(gen/return nil)
     (gen/keyword)
     (gen/string-alphanumeric)
     (gen/map (gen/keyword) (gen/large-integer* {}))
     (gen/vector (invalid-number-value) 1 8)]))

(defn sqids-input
  "Builds valid and invalid Sqids config inputs for encode call args."
  [min-length-limit]
  (gen/frequency
    [[7 (s/gen init-sqids-spec-key)]
     [3 (init-generators/invalid-sqids-input min-length-limit)]]))

(defn numbers-input
  "Builds valid and invalid numeric inputs for encode call args."
  []
  (gen/frequency
    [[7 (s/gen nat-ints-spec-key)]
     [3 (invalid-numbers-input)]]))
