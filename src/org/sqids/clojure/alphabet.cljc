(ns org.sqids.clojure.alphabet
  (:require
    [org.sqids.clojure.platform :as platform]))

(def default
  "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn nth-char-code
  [alpha-vec i]
  (platform/char-code (nth alpha-vec i)))

(defn cut
  [alphabet offset]
  (concat (subs alphabet offset) (subs alphabet 0 offset)))

(defn reverse-str
  [s]
  (apply str (reverse s)))

(defn ^:private shuffle-once
  [alpha-len acc i]
  (let [j        (- alpha-len i 1)
        ith-code (nth-char-code acc i)
        jth-code (nth-char-code acc j)
        r        (mod (+ (* i j) ith-code jth-code) alpha-len)]
    (assoc acc i (nth acc r) r (nth acc i))))

(defn consistent-shuffle
  [alphabet]
  (let [alpha-len (count alphabet)
        alpha-vec (vec alphabet)]
    (->> (range 0 (dec alpha-len))
         (reduce (partial shuffle-once alpha-len) alpha-vec)
         (apply str))))
