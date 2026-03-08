(ns org.sqids.clojure.generators.alphabet
  (:require
    [clojure.spec.gen.alpha :as gen]))

(def printable-characters
  "Printable ASCII characters used by alphabet-adjacent generators."
  (mapv char (range 33 127)))

(defn alphabet
  "Generates valid, distinct alphabets from the provided alphabet domain."
  [default-alphabet min-length]
  (gen/frequency
    [[2 (gen/return default-alphabet)]
     [2 (gen/fmap (partial apply str)
                  (gen/shuffle (vec default-alphabet)))]
     [6 (gen/fmap (partial apply str)
                  (gen/vector-distinct
                    (gen/elements printable-characters)
                    {:min-elements min-length
                     :max-elements (count printable-characters)}))]]))
