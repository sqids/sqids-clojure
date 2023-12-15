(ns org.sqids.clojure.decoding
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.encoding :as-alias encoding]
    [org.sqids.clojure.init :as-alias init]
    [org.sqids.clojure.platform :as platform]))

(defn ^:private ->number
  [id from to alphabet]
  (->
    (reduce
      (fn [acc i]
        (platform/+-safe
          (platform/*-safe acc (count alphabet))
          (str/index-of alphabet (nth id i))))
      0
      (range from to))))

(defn decode
  [{:keys [sqids sqid]}]
  (let [{:keys [alphabet]} sqids]
    (if (or (empty? sqid)
            (not (set/subset? (set sqid) (set alphabet))))
      []
      (let [prefix   (first sqid)
            offset   (str/index-of alphabet prefix)
            alphabet (-> alphabet
                         (alphabet/cut offset)
                         alphabet/reverse-str)]
        (loop [idx     1
               numbers []
               alpha   alphabet]
          (let [sep     (first alpha)
                sep-idx (or (str/index-of sqid sep idx)
                            (count sqid))]
            (if (= idx sep-idx)
              numbers
              (let [numbers' (conj numbers (->number sqid idx sep-idx (subs alpha 1)))
                    idx'     (inc sep-idx)]
                (if (< idx' (count sqid))
                  (recur idx' numbers' (alphabet/consistent-shuffle alpha))
                  numbers')))))))))
