(ns org.sqids.clojure.benchmark
  "Allocation regression checks with illustrative, non-gating JVM timings."
  (:gen-class)
  (:require
    [org.sqids.clojure :as sqids])
  (:import
    (com.sun.management
      ThreadMXBean)
    (java.lang.management
      ManagementFactory)
    (java.util.concurrent.atomic
      AtomicReference)))

(set! *warn-on-reflection* true)

(def ^:private ^AtomicReference sink
  "Retains each result so benchmark work cannot be eliminated by the compiler."
  (AtomicReference.))

(defn- measure
  "Measures one batch, including identical result-consumption overhead in every call."
  [^ThreadMXBean allocation-bean n f]
  (let [thread-id (.getId (Thread/currentThread))
        before-bytes (.getThreadAllocatedBytes allocation-bean thread-id)
        before-time (System/nanoTime)]
    (dotimes [_ n]
      (.lazySet sink (f)))
    {:microseconds (/ (- (System/nanoTime) before-time) (* 1000.0 n))
     :bytes (/ (- (.getThreadAllocatedBytes allocation-bean thread-id) before-bytes)
               (double n))}))

(defn- median
  "Returns the middle of an odd number of measurements."
  [values]
  (nth (sort values) (quot (count values) 2)))

(defn- sample
  "Reports median time and allocation over three batches after explicit warmup."
  [allocation-bean label warmup iterations f]
  (dotimes [_ warmup]
    (.lazySet sink (f)))
  (let [batches (repeatedly 3 #(measure allocation-bean iterations f))
        allocated (median (map :bytes batches))
        microseconds (median (map :microseconds batches))]
    (println (format "%s: %.2f us/call, %.0f bytes/call" label microseconds allocated))
    allocated))

(defn- allocation-budgets
  "Checks absolute allocation budgets for short IDs, multi-digit values and bigints.

  Budgets include public validation and the default blocklist, with one reused
  configuration. They are deliberately independent of another implementation
  path: a uniform increase in allocation must fail this check. Ceilings allow
  modest JIT variation while protecting the encoder's recursive digit path.
  The added JDK 21 baselines are about 2 KB for multi-digit values and 6 KB
  for a 128-bit integer; their ceilings leave approximately 25% headroom."
  [allocation-bean]
  (let [config (sqids/sqids)]
    (doseq [[numbers encode-budget decode-budget] [[[1] 1400 1400]
                                                   [[1 2] 1800 1800]
                                                   [[1 2 3] 2100 2100]
                                                   [[1000000000 2000000000 3000000000] 2400 2600]
                                                   [[340282366920938463463374607431768211455N] 7400 7100]]]
      (let [id (sqids/encode config numbers)]
        (when-not (= numbers (sqids/decode config id))
          (throw (ex-info "Benchmark fixture failed to round-trip" {:numbers numbers})))
        (doseq [[operation budget f] [[:encode encode-budget #(sqids/encode config numbers)]
                                      [:decode decode-budget #(sqids/decode config id)]]]
          (let [allocated (sample allocation-bean (str numbers " " (name operation)) 30000 10000 f)]
            (when (> allocated budget)
              (throw (ex-info "ID allocation budget exceeded"
                              {:operation operation :numbers numbers :bytes allocated :budget budget})))))))))

(defn- allocation-scaling
  "Checks allocation growth over a sixteenfold increase in long input length."
  [allocation-bean]
  (let [config (sqids/sqids {:alphabet "abc" :block-list #{}})
        samples (mapv
                  (fn [size]
                    (let [numbers (vec (repeat size 0))
                          id (sqids/encode config numbers)]
                      (when-not (= numbers (sqids/decode config id))
                        (throw (ex-info "Scaling fixture failed to round-trip" {:size size})))
                      {:encode (sample allocation-bean (str size " numbers encode") 5 3
                                       #(sqids/encode config numbers))
                       :decode (sample allocation-bean (str size " numbers decode") 5 3
                                       #(sqids/decode config id))}))
                  [4000 16000 64000])]
    (doseq [operation [:encode :decode]
            [small large] (partition 2 1 samples)]
      (let [ratio (/ (operation large) (operation small))]
        (println (format "%s allocation growth on quadrupled input: %.2fx" (name operation) ratio))
        ;; Linear code approaches 4x; 10% headroom accommodates array/vector growth.
        ;; Quadratic concatenation grows toward 16x and n^1.5 allocation toward 8x.
        (when (> ratio 4.4)
          (throw (ex-info "Long-input allocation growth exceeded linear budget"
                          {:operation operation :ratio ratio :budget 4.4})))))))

(defn -main
  "Runs allocation checks on HotSpot; wall-clock timings are informational only."
  [& _]
  (let [allocation-bean (ManagementFactory/getThreadMXBean)]
    (when-not (and (instance? ThreadMXBean allocation-bean)
                   (.isThreadAllocatedMemorySupported ^ThreadMXBean allocation-bean))
      (throw (ex-info "Thread allocation metrics are unavailable on this JVM" {})))
    (.setThreadAllocatedMemoryEnabled ^ThreadMXBean allocation-bean true)
    (println "Reused default configuration; median of three batches; output consumed.")
    (allocation-budgets allocation-bean)
    (allocation-scaling allocation-bean))
  (shutdown-agents))
