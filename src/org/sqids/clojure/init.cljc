(ns org.sqids.clojure.init
  (:require
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.encoding :as encoding]))

(def default-options
  {:alphabet   alphabet/default
   :min-length encoding/default-min-length
   :block-list block-list/default})

(defn sqids
  [options]
  (as-> default-options v
        (merge v options)
        (update v :block-list block-list/remove-invalid-words (:alphabet v))
        (update v :alphabet alphabet/consistent-shuffle)))
