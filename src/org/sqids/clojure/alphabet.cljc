(ns org.sqids.clojure.alphabet
  (:require
    [clojure.spec.alpha :as s]
    [org.sqids.clojure.generators.alphabet :as alphabet-generators]
    [org.sqids.clojure.platform :as platform]))

(def default
  "Default Sqids alphabet."
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(def min-length
  "Minimum allowed alphabet size."
  3)

(def max-char-code
  "Highest single-byte character code accepted in Sqids alphabets."
  127)

(defn rotate-left
  "Returns `alphabet` rotated left by `offset` characters."
  [alphabet offset]
  (str (subs alphabet offset) (subs alphabet 0 offset)))

(defn reverse-str
  "Returns `s` with characters reversed."
  [s]
  (apply str (reverse s)))

(defn consistent-shuffle
  "Applies the deterministic Sqids alphabet shuffle."
  [alphabet]
  (let [size (count alphabet)]
    (loop [alphabet-chars (vec alphabet)
           left-index 0
           right-index (dec size)]
      (if (<= right-index 0)
        (apply str alphabet-chars)
        (let [swap-index (mod (+ (* left-index right-index)
                                 (platform/char-code (nth alphabet-chars left-index))
                                 (platform/char-code (nth alphabet-chars right-index)))
                              size)
              left-char (nth alphabet-chars left-index)
              swap-char (nth alphabet-chars swap-index)]
          (recur (assoc alphabet-chars left-index swap-char swap-index left-char)
                 (inc left-index)
                 (dec right-index)))))))

(s/def ::alphabet-string
  string?)

(s/def ::alphabet-distinct
  #(apply distinct? %))

(s/def ::alphabet-min-length
  #(>= (count %) min-length))

(s/def ::alphabet-no-multibyte
  #(every? (fn [character] (<= (platform/char-code character) max-char-code)) %))

(s/def ::alphabet
  (s/with-gen
    (s/and ::alphabet-string
           ::alphabet-distinct
           ::alphabet-min-length
           ::alphabet-no-multibyte)
    #(alphabet-generators/alphabet default min-length)))
