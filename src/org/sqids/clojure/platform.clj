(ns org.sqids.clojure.platform
  (:require
    [clojure.spec.alpha :as s]))

(def char-code
  int)

(def max-value
  ##Inf)

(def +-safe
  +')

(def *-safe
  *')

(s/def ::integer
  integer?)
