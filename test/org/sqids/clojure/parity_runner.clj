(ns org.sqids.clojure.parity-runner
  (:gen-class)
  (:require
    [clojure.test :as t]
    [org.sqids.clojure.parity]))

(set! *warn-on-reflection* true)

(defn -main
  "Runs the sqids-spec parity test namespace and exits nonzero on failure."
  [& _]
  (let [result (t/run-tests 'org.sqids.clojure.parity)]
    (shutdown-agents)
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
