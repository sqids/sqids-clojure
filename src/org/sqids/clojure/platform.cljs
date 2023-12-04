(ns org.sqids.clojure.platform
  (:require
    ["sqids" :as sqids]
    ["sqids$default" :as Sqids]
    [clojure.spec.alpha :as s]))

(def default-alphabet
  (js->clj sqids/defaultOptions.alphabet))

(def default-min-length
  sqids/defaultOptions.minLength)

(def default-block-list
  (js->clj sqids/defaultOptions.blocklist))

(def class
  Sqids)

(defn sqids
  [{:keys [alphabet min-length block-list]}]
  (Sqids. (clj->js {:alphabet  alphabet
                    :minLength min-length
                    :blocklist block-list})))

(defn encode
  [^js instance numbers]
  (js->clj (.encode instance (clj->js numbers))))

(defn decode
  [^js instance sqid]
  (js->clj (.decode instance (clj->js sqid))))

(defn byte-count
  [s]
  (.-size (js/Blob. [s])))

(def max-value
  js/Number.MAX_SAFE_INTEGER)

(def max-value+1
  (inc max-value))

(s/def ::ints-elem
  (s/and number? #(= % (long %))))
