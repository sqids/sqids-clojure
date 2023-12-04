(ns org.sqids.clojure.platform
  (:refer-clojure :exclude [class])
  (:require
    [clojure.spec.alpha :as s])
  (:import
    (org.sqids
      Sqids
      Sqids$Builder)))

(def ^:const default-alphabet
  Sqids$Builder/DEFAULT_ALPHABET)

(def ^:const default-min-length
  Sqids$Builder/DEFAULT_MIN_LENGTH)

(def ^:const default-block-list
  Sqids$Builder/DEFAULT_BLOCK_LIST)

(def ^:const class
  Sqids)

(defn sqids
  [{:keys [alphabet min-length block-list]}]
  (.. (Sqids/builder)
      (alphabet alphabet)
      (minLength min-length)
      (blockList block-list)
      build))

(defn encode
  [^org.sqids.Sqids instance numbers]
  (.encode instance numbers))

(defn decode
  [^org.sqids.Sqids instance sqid]
  (.decode instance sqid))

(defn byte-count
  [^String s]
  (count (.getBytes s)))

(def ints-predicate
  int?)

(def ^:const max-value
  Long/MAX_VALUE)

(def ^:const max-value+1
  (inc' max-value))

(s/def ::ints-elem
  int?)
