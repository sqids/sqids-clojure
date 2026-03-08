(ns org.sqids.clojure.generators.block-list
  (:require
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]))

(def block-list-word-characters
  "Characters used for generated block-list words."
  (vec "abcdefghijklmnopqrstuvwxyz0123456789"))

(defn domain-characters
  "Returns lowercase characters that survive block-list normalization."
  [alphabet-option]
  (or (->> alphabet-option
           str/lower-case
           distinct
           vec
           not-empty)
      block-list-word-characters))

(defn ^:private word
  "Generates a block-list word compatible with `alphabet-option` normalization."
  [alphabet-option min-word-length]
  (gen/fmap (partial apply str)
            (gen/vector (gen/elements (domain-characters alphabet-option))
                        min-word-length
                        12)))

(defn ^:private invalid-word
  "Generates words likely to be filtered during block-list normalization."
  []
  (gen/fmap (partial apply str)
            (gen/vector (gen/elements block-list-word-characters) 0 12)))

(defn block-list
  "Builds a block-list generator for `alphabet-option` plus some invalid noise."
  [{:keys [alphabet-option min-word-length]}]
  (gen/frequency
    [[2 (gen/return #{})]
     [7 (gen/fmap set
                  (gen/vector-distinct
                    (word alphabet-option min-word-length)
                    {:min-elements 1 :max-elements 8}))]
     [1 (gen/fmap set
                  (gen/vector-distinct
                    (invalid-word)
                    {:min-elements 1 :max-elements 4}))]]))
