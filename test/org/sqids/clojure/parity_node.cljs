(ns org.sqids.clojure.parity-node
  (:require
    [org.sqids.clojure.parity-evaluator :as evaluator]))

(defn -main
  "Reads the shared corpus from JSON and emits this compiled implementation's results."
  [request-path]
  (let [^js fs (js/require "fs")
        requests (js->clj (js/JSON.parse (.readFileSync fs request-path "utf8")) :keywordize-keys true)]
    (.log js/console (js/JSON.stringify (clj->js (mapv evaluator/response requests))))))

(set! *main-cli-fn* -main)
