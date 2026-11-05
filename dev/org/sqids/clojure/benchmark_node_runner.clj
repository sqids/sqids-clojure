(ns org.sqids.clojure.benchmark-node-runner
  (:require
    [cljs.build.api :as cljs]
    [clojure.java.shell :as shell]))

(defn run
  "Compiles the Node benchmark with production optimizations and runs it."
  [_]
  (cljs/build {:main 'org.sqids.clojure.benchmark-node
               :target :nodejs
               :optimizations :advanced
               :output-dir "target/benchmark-node/cljs"
               :output-to "target/benchmark-node/runner.js"})
  (let [{:keys [exit out err]} (shell/sh "node" "target/benchmark-node/runner.js")]
    (print out)
    (binding [*out* *err*]
      (print err))
    (when-not (zero? exit)
      (throw (ex-info "Node benchmark failed" {:exit exit}))))
  (shutdown-agents))
