(ns org.sqids.clojure.platform
  (:require
    [clojure.spec.alpha :as s]))

(defn char-code
  [^js c]
  (.charCodeAt c 0))

(def max-value
  js/Number.MAX_SAFE_INTEGER)

(def +-safe
  +)

(def *-safe
  *)

(s/def ::integer
  (s/with-gen (s/and number? #(== (mod % 1) 0)) #(s/gen integer?)))
