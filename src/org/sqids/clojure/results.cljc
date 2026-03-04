(ns org.sqids.clojure.results
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [org.sqids.clojure.platform :as platform]))

(def result-key-namespace
  "Namespace expected for top-level keys in result envelopes."
  (namespace ::_))

(defn namespaced-results-keys?
  "Returns true when all top-level keys in `result` are results-namespace keywords."
  [result]
  (and (map? result)
       (every? qualified-keyword? (keys result))
       (every? #(= result-key-namespace (namespace %)) (keys result))))

(defn ^:private normalize-details
  "Normalizes error details into a map payload."
  [details]
  (cond
    (map? details)
    details

    (nil? details)
    {}

    :else
    {:value details}))

(defn ^:private trimmed-message
  "Returns `message` without surrounding whitespace when it is non-blank."
  [message]
  (let [trimmed (some-> message str/trim)]
    (when (seq trimmed)
      trimmed)))

(defn ^:private spec-explain-message
  "Builds a human-readable spec explanation when `details` includes spec context."
  [details]
  (when-let [spec-key (::s/spec details)]
    (trimmed-message (s/explain-str spec-key (::s/value details)))))

(defn ^:private invalid-input-message
  "Selects the richest invalid-input message available from `details` and `cause`."
  [details cause]
  (or (spec-explain-message details)
      (some-> cause platform/exception-message trimmed-message)
      "Invalid input"))

(defn ok
  "Builds a successful result envelope."
  [value]
  {::status ::ok
   ::value  value})

(defn error
  "Builds an error result envelope."
  [code stage message details]
  {::status  ::error
   ::code    code
   ::stage   stage
   ::message message
   ::details (normalize-details details)})

(defn ok?
  "Returns true when `result` is a successful envelope."
  [result]
  (= ::ok (::status result)))

(defn invalid-input
  "Builds an invalid-input error result from explain-data or an exception."
  ([cause]
   (let [details (normalize-details (ex-data cause))]
     (invalid-input ::validation
                    (::s/value details)
                    details
                    (invalid-input-message details cause))))
  ([stage input explain-data]
   (invalid-input stage input explain-data nil))
  ([stage input explain-data message]
   (let [details (normalize-details explain-data)]
     (assoc (error ::invalid-input
                   stage
                   (or (trimmed-message message)
                       (invalid-input-message details nil))
                   details)
            ::input    input
            ::problems (vec (::s/problems details))))))

(defn encode-max-attempts
  "Builds an encode max-attempts error result from details or an exception."
  ([cause]
   (encode-max-attempts ::encode-run-stage (ex-data cause)))
  ([stage details]
   (let [normalized-details (normalize-details details)]
     (assoc (error ::encode-max-attempts
                   stage
                   "Reached max attempts to re-generate the ID"
                   normalized-details)
            ::increment (:increment normalized-details)
            ::numbers   (:numbers normalized-details)))))

(defn unexpected
  "Builds an unexpected-exception result and preserves the original exception."
  ([cause]
   (unexpected ::unexpected cause))
  ([stage cause]
   (assoc (error ::unexpected-exception
                 stage
                 (platform/exception-message cause)
                 (ex-data cause))
          ::exception cause)))

(defn attempt
  "Evaluates `thunk`, converting unexpected exceptions into error envelopes."
  [stage thunk]
  (platform/try-call thunk
                     (fn [cause]
                       (unexpected stage cause))))

(defn bind
  "Invokes `f` for successful results and passes through error results unchanged."
  ([result f]
   (bind result ::bind f))
  ([result stage f]
   (if (ok? result)
     (attempt stage #(f (::value result)))
     result)))

(defn conform
  "Conforms `input` with `spec-key` and returns either ::ok or ::invalid-input."
  [spec-key input stage]
  (attempt stage
           #(let [conformed (s/conform spec-key input)]
              (if (s/invalid? conformed)
                (invalid-input stage input (s/explain-data spec-key input))
                (ok conformed)))))

(defn ok-result-for
  "Returns a spec matching ::ok result envelopes with values conforming to `value-spec`."
  [value-spec]
  (s/and ::ok-result
         #(s/valid? value-spec (::value %))))

(s/def ::status
  #{::ok ::error})

(s/def ::value
  any?)

(s/def ::code
  #{::invalid-input ::encode-max-attempts ::unexpected-exception})

(s/def ::stage
  keyword?)

(s/def ::message
  string?)

(s/def ::details
  map?)

(s/def ::exception
  some?)

(s/def ::input
  any?)

(s/def ::problems
  vector?)

(s/def ::increment
  integer?)

(s/def ::numbers
  sequential?)

(def ok-result-keys
  "Allowed top-level keys for successful result envelopes."
  #{::status ::value})

(def error-result-keys
  "Allowed top-level keys for error result envelopes."
  #{::status
    ::code
    ::stage
    ::message
    ::details
    ::exception
    ::input
    ::problems
    ::increment
    ::numbers})

(defn ^:private allowed-result-keys?
  "Returns true when `result` only contains `allowed-keys`."
  [allowed-keys result]
  (every? allowed-keys (keys result)))

(s/def ::result-map
  (s/and map?
         namespaced-results-keys?))

(s/def ::ok-result-shape
  (s/and (s/keys :req [::status ::value])
         #(= ::ok (::status %))
         #(allowed-result-keys? ok-result-keys %)))

(s/def ::error-base-shape
  (s/and (s/keys :req [::status ::code ::stage ::message ::details]
                 :opt [::exception
                       ::input
                       ::problems
                       ::increment
                       ::numbers])
         #(= ::error (::status %))
         #(allowed-result-keys? error-result-keys %)))

(defmulti error-shape-spec
  "Dispatches error envelope shape specs by `::code`."
  ::code)

(defmethod error-shape-spec ::invalid-input
  [_]
  (s/and ::error-base-shape
         (s/keys :req [::input ::problems])))

(defmethod error-shape-spec ::encode-max-attempts
  [_]
  (s/and ::error-base-shape
         (s/keys :req [::increment ::numbers])))

(defmethod error-shape-spec ::unexpected-exception
  [_]
  (s/and ::error-base-shape
         (s/keys :req [::exception])))

(s/def ::error-result-shape
  (s/multi-spec error-shape-spec ::code))

(defmulti result-shape-spec
  "Dispatches result envelope shape specs by `::status`."
  ::status)

(defmethod result-shape-spec ::ok
  [_]
  ::ok-result-shape)

(defmethod result-shape-spec ::error
  [_]
  ::error-result-shape)

(s/def ::result
  (s/and ::result-map
         (s/multi-spec result-shape-spec ::status)))

(s/def ::ok-result
  (s/and ::result
         #(= ::ok (::status %))))

(s/def ::error-result
  (s/and ::result
         #(= ::error (::status %))))
