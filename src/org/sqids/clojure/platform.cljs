(ns org.sqids.clojure.platform
  (:require
    ["sqids" :as sqids]
    ["sqids$default" :as Sqids]))

(def default-alphabet
  (js->clj sqids/defaultOptions.alphabet))

(def ^:const default-min-length
  sqids/defaultOptions.minLength)

(def default-block-list
  (js->clj sqids/defaultOptions.blocklist))

(def ^:const class
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

(def ^:const max-value
  js/Number.MAX_SAFE_INTEGER)

(def ^:const over-max-value
  (inc max-value))
