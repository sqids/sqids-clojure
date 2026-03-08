(ns org.sqids.clojure.parity
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.test :as t]
    [clojure.test.check.generators :as tcgen]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.init :as init]))

(set! *warn-on-reflection* true)

(def js-safe-int-max
  "Largest non-negative integer shared by the JS reference implementation."
  9007199254740991)

(def generated-roundtrip-case-count
  "Number of spec-generated shared-domain roundtrip parity cases."
  48)

(def generated-invalid-decode-case-count
  "Number of spec-generated invalid-character decode parity cases."
  16)

(def invalid-reference-sqid
  "Sqid character sequence guaranteed to be invalid for generated alphabets."
  "\u0100")

(def reference-runner-path
  "Checked-in Node helper that evaluates requests with the sqids-spec reference implementation."
  (.getAbsolutePath
    (io/file "test-resources" "org" "sqids" "clojure" "sqids_spec_runner.mjs")))

(s/def ::id
  string?)

(s/def ::op
  #{"roundtrip" "decode" "encode" "sqids"})

(s/def ::alphabet
  string?)

(s/def ::min-length
  integer?)

(s/def ::block-list
  (s/coll-of string? :kind vector?))

(s/def ::options
  (s/keys :opt-un [::alphabet ::min-length ::block-list]))

(s/def ::numbers
  (s/coll-of integer? :kind vector?))

(defn options->wire
  "Converts public Clojure options to a JSON-friendly wire map."
  [options]
  (cond-> {}
    (contains? options :alphabet)
    (assoc :alphabet (:alphabet options))

    (contains? options :min-length)
    (assoc :min-length (:min-length options))

    (contains? options :block-list)
    (assoc :block-list (vec (sort (:block-list options))))))

(defn wire->options
  "Converts a wire map back into public Clojure Sqids options."
  [wire-options]
  (cond-> {}
    (contains? wire-options :alphabet)
    (assoc :alphabet (:alphabet wire-options))

    (contains? wire-options :min-length)
    (assoc :min-length (:min-length wire-options))

    (contains? wire-options :block-list)
    (assoc :block-list (set (:block-list wire-options)))))

(defn valid-wire-options?
  "Returns true when `wire-options` conform to the public Sqids options spec."
  [wire-options]
  (s/valid? ::init/options (wire->options wire-options)))

(s/def ::wire-options
  (s/with-gen
    valid-wire-options?
    #(gen/fmap options->wire (s/gen ::init/options))))

(s/def ::shared-number
  (s/with-gen
    (s/and integer? #(<= 0 % js-safe-int-max))
    #(gen/large-integer* {:min 0 :max js-safe-int-max})))

(s/def ::shared-numbers
  (s/with-gen
    (s/coll-of ::shared-number :kind vector?)
    #(gen/vector (s/gen ::shared-number) 0 8)))

(s/def ::sqid
  string?)

(defmulti request-shape
  "Dispatches parity request specs by operation."
  :op)

(defmethod request-shape "roundtrip"
  [_]
  (s/keys :req-un [::id ::op ::options ::numbers]))

(defmethod request-shape "decode"
  [_]
  (s/keys :req-un [::id ::op ::options ::sqid]))

(defmethod request-shape "encode"
  [_]
  (s/keys :req-un [::id ::op ::options ::numbers]))

(defmethod request-shape "sqids"
  [_]
  (s/keys :req-un [::id ::op ::options]))

(s/def ::request
  (s/multi-spec request-shape :op))

(s/def ::status
  #{"ok" "error"})

(s/def ::message
  string?)

(defn roundtrip-value?
  "Returns true when `value` has the shared-domain roundtrip parity shape."
  [value]
  (and (map? value)
       (s/valid? ::sqid (:sqid value))
       (s/valid? ::shared-numbers (:numbers value))))

(defn ok-response-value?
  "Returns true when an ok response value matches the request operation."
  [{:keys [op value]}]
  (case op
    "roundtrip" (roundtrip-value? value)
    "decode" (s/valid? ::shared-numbers value)
    "encode" (s/valid? ::sqid value)
    "sqids" (= "initialized" value)
    false))

(s/def ::ok-response
  (s/and
    (s/keys :req-un [::id ::op ::status])
    #(contains? % :value)
    #(= "ok" (:status %))
    ok-response-value?))

(s/def ::error-response
  (s/and
    (s/keys :req-un [::id ::op ::status ::message])
    #(= "error" (:status %))))

(s/def ::response
  (s/or :ok ::ok-response
        :error ::error-response))

(defn sqids-spec-dir
  "Returns the checked-out sqids-spec directory required for parity tests."
  []
  (or (System/getenv "SQIDS_SPEC_DIR")
      (throw (ex-info "SQIDS_SPEC_DIR must point to a checked-out sqids-spec repository"
                      {}))))

(defn generate-samples
  "Generates deterministic `sample-count` values from `generator` using `seed`."
  [generator sample-count seed]
  (mapv (fn [offset]
          (tcgen/generate generator
                          12
                          (+ seed offset)))
        (range sample-count)))

(defn fixed-requests
  "Returns curated parity requests for the shared successful spec surface."
  []
  [{:id "default-simple"
    :op "roundtrip"
    :options {}
    :numbers [1 2 3]}
   {:id "default-blocklist"
    :op "roundtrip"
    :options {}
    :numbers [4572721]}
   {:id "custom-alphabet"
    :op "roundtrip"
    :options {:alphabet "0123456789abcdef"}
    :numbers [1 2 3]}
   {:id "custom-min-length"
    :op "roundtrip"
    :options {:min-length 10}
    :numbers [1 2 3]}
   {:id "custom-block-list"
    :op "roundtrip"
    :options {:block-list ["ArUO"]}
    :numbers [100000]}
   {:id "invalid-character"
    :op "decode"
    :options {}
    :sqid "*"}])

(defn generated-roundtrip-requests
  "Returns spec-generated roundtrip requests in the shared JS-safe domain."
  []
  (->> (generate-samples (gen/tuple (s/gen ::wire-options)
                                    (s/gen ::shared-numbers))
                         generated-roundtrip-case-count
                         42000)
       (map-indexed (fn [index [wire-options shared-numbers]]
                      {:id             (format "generated-roundtrip-%02d" index)
                       :op             "roundtrip"
                       :options        wire-options
                       :numbers        shared-numbers}))
       vec))

(defn generated-invalid-decode-requests
  "Returns spec-generated invalid-character decode requests."
  []
  (->> (generate-samples (s/gen ::wire-options)
                         generated-invalid-decode-case-count
                         43000)
       (map-indexed (fn [index wire-options]
                      {:id           (format "generated-invalid-decode-%02d" index)
                       :op           "decode"
                       :options      wire-options
                       :sqid         invalid-reference-sqid}))
       vec))

(defn request->wire
  "Normalizes internal request maps to the JSON wire shape used by the Node helper."
  [request]
  (cond-> {:id      (:id request)
           :op      (:op request)
           :options (:options request)}
    (:numbers request)
    (assoc :numbers (:numbers request))

    (:sqid request)
    (assoc :sqid (:sqid request))))

(defn local-response
  "Evaluates a single parity request against this Clojure implementation."
  [request]
  (let [wire-request (request->wire request)
        options      (wire->options (:options wire-request))]
    (try
      (case (:op wire-request)
        "roundtrip"
        (let [sqids-config (sut/sqids options)
              sqid         (sut/encode sqids-config (:numbers wire-request))]
          {:id     (:id wire-request)
           :op     (:op wire-request)
           :status "ok"
           :value  {:sqid sqid
                    :numbers (vec (sut/decode sqids-config sqid))}})

        "decode"
        (let [sqids-config (sut/sqids options)]
          {:id     (:id wire-request)
           :op     (:op wire-request)
           :status "ok"
           :value  (vec (sut/decode sqids-config (:sqid wire-request)))})

        "encode"
        (let [sqids-config (sut/sqids options)]
          {:id     (:id wire-request)
           :op     (:op wire-request)
           :status "ok"
           :value  (sut/encode sqids-config (:numbers wire-request))})

        "sqids"
        (do
          (sut/sqids options)
          {:id     (:id wire-request)
           :op     (:op wire-request)
           :status "ok"
           :value  "initialized"}))
      (catch Exception error
        {:id      (:id wire-request)
         :op      (:op wire-request)
         :status  "error"
         :message (.getMessage error)}))))

(defn parse-reference-responses
  "Parses the JSON emitted by the sqids-spec Node helper."
  [json-output]
  (json/read-str json-output :key-fn keyword))

(defn reference-responses
  "Evaluates `requests` with the checked-out sqids-spec reference implementation."
  [requests]
  (let [spec-dir     (sqids-spec-dir)
        request-file (java.io.File/createTempFile "sqids-spec-parity" ".json")
        wire-requests (mapv request->wire requests)]
    (try
      (spit request-file (json/write-str wire-requests))
      (let [{:keys [exit out err]} (shell/sh "npx"
                                             "vite-node"
                                             "--script"
                                             reference-runner-path
                                             (.getAbsolutePath request-file)
                                             spec-dir
                                             :dir spec-dir)]
        (when-not (zero? exit)
          (throw (ex-info "sqids-spec reference runner failed"
                          {:exit exit
                           :out  out
                           :err  err})))
        (parse-reference-responses out))
      (finally
        (.delete request-file)))))

(defn assert-valid!
  "Asserts that `value` conforms to `spec`."
  [spec value]
  (t/is (s/valid? spec value)
        (with-out-str (s/explain spec value))))

(defn assert-parity!
  "Runs `requests` through both implementations and asserts matching results."
  [requests]
  (doseq [request requests]
    (assert-valid! ::request (request->wire request)))
  (let [expected (mapv local-response requests)
        actual   (reference-responses requests)]
    (t/is (= (count expected) (count actual)))
    (doseq [response expected]
      (assert-valid! ::response response))
    (doseq [response actual]
      (assert-valid! ::response response))
    (doseq [[request expected-response actual-response] (map vector requests expected actual)]
      (t/testing (:id request)
        (t/is (= expected-response actual-response))))))

(t/deftest sqids-spec-parity-test
  (assert-parity! (into []
                        cat
                        [(fixed-requests)
                         (generated-roundtrip-requests)
                         (generated-invalid-decode-requests)])))
