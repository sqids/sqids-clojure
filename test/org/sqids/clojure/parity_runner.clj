(ns org.sqids.clojure.parity-runner
  (:require
    [cljs.build.api :as cljs]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.test :as t]
    [org.sqids.clojure.parity :as parity]
    [org.sqids.clojure.parity-evaluator :as evaluator]))

(set! *warn-on-reflection* true)

(defn- command-results
  "Runs a JSON-producing process, failing immediately if the runner fails."
  [& args]
  (let [{:keys [exit out err]} (apply shell/sh args)]
    (when-not (zero? exit)
      (throw (ex-info "Parity runner failed" {:command args :exit exit :out out :err err})))
    (json/read-str out :key-fn keyword)))

(defn- check-results
  "Checks successful values, expected failures and independent roundtrip inputs."
  [platform requests expected actual]
  (t/is (= (count requests) (count expected) (count actual)) platform)
  (doseq [[request reference result] (map vector requests expected actual)]
    (t/testing (str platform " " (:id request))
      (t/is (= reference result))
      (t/is (= (boolean (:expect-error? request)) (boolean (:error result))))
      (when (= "roundtrip" (:op request))
        (t/is (= (:numbers request) (get-in result [:value :numbers])))))))

(defn run
  "Compiles CLJS with advanced optimization and checks both platforms against upstream."
  [_]
  (let [spec-dir (or (System/getenv "SQIDS_SPEC_DIR")
                     (throw (ex-info "Set SQIDS_SPEC_DIR to a sqids-spec checkout" {})))
        requests (parity/requests)
        request-path (.getAbsolutePath (io/file "target/parity/requests.json"))
        runner-path (.getAbsolutePath (io/file "test-resources/org/sqids/clojure/sqids_spec_runner.mjs"))]
    (println "Compiling ClojureScript parity runner with advanced optimization...")
    (cljs/build {:main 'org.sqids.clojure.parity-node
                 :target :nodejs
                 :optimizations :advanced
                 :infer-externs true
                 :output-dir "target/parity/cljs-advanced"
                 :output-to "target/parity/runner.js"})
    (spit request-path (json/write-str requests))
    (let [reference (command-results "npx" "--no-install" "vite-node" "--script"
                                     runner-path request-path spec-dir :dir spec-dir)
          node (command-results "node" "target/parity/runner.js" request-path)]
      (binding [t/*report-counters* (ref t/*initial-report-counters*)]
        (check-results "JVM" requests reference (mapv evaluator/response requests))
        (check-results "CLJS" requests reference node)
        (println (count requests) "requests per platform;" @t/*report-counters*)
        (when (pos? (+ (:fail @t/*report-counters*) (:error @t/*report-counters*)))
          (throw (ex-info "Sqids parity failed" @t/*report-counters*)))))
    (shutdown-agents)))
