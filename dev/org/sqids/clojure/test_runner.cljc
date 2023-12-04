(ns org.sqids.clojure.test-runner
  (:require
    [clojure.test :as t]
    [org.sqids.clojure.alphabet-test]
    [org.sqids.clojure.block-list-test]
    [org.sqids.clojure.encoding-test]
    [org.sqids.clojure.min-length-test]
    [org.sqids.clojure.spec-test]))

(defn run-all-tests
  []
  (t/run-all-tests #"^org\.sqids\.clojure\..*-test$"))
