(ns org.sqids.clojure.benchmark-node
  "Informational Node timings for the advanced-compiled public API."
  (:require
    [org.sqids.clojure :as sqids]))

(def ^:private sink
  "Retains benchmark results so the work remains observable."
  (volatile! nil))

(defn- sample
  "Reports the median of three batches after warmup; never gates on wall-clock time."
  [label f]
  (dotimes [_ 20000]
    (vreset! sink (f)))
  (let [timings (doall
                  (repeatedly 3
                              (fn []
                                (let [start (js/performance.now)]
                                  (dotimes [_ 10000]
                                    (vreset! sink (f)))
                                  (/ (* 1000 (- (js/performance.now) start)) 10000)))))
        median (nth (sort timings) 1)]
    (println label (str (.toFixed median 2) " us/call"))))

(defn -main
  "Measures ordinary and multi-digit IDs with reused default and unfiltered configurations."
  [& _]
  (println "Node" (aget js/process "version") "; advanced compilation; output consumed; timings informational.")
  (doseq [[label options numbers]
          [["default / three numbers" {} [1 2 3]]
           ["default / multi-digit" {} [1000000000 2000000000 3000000000]]
           ["default / maximum safe integer" {} [9007199254740991]]
           ["empty blocklist / three numbers" {:block-list #{}} [1 2 3]]]]
    (let [config (sqids/sqids options)
          id (sqids/encode config numbers)]
      (when-not (= numbers (sqids/decode config id))
        (throw (ex-info "Node benchmark fixture failed to round-trip" {:label label})))
      (sample (str label " encode:") #(sqids/encode config numbers))
      (sample (str label " decode:") #(sqids/decode config id))))
  (println "Consumed final result length:" (count @sink)))

(set! *main-cli-fn* -main)
