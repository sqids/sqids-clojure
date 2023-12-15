(ns org.sqids.clojure.test-util
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as stest]
    [clojure.test :as t]
    [clojure.test.check]
    [clojure.test.check.properties]
    [expound.alpha :as expound]
    [#?(:clj orchestra.spec.test :cljs orchestra-cljs.spec.test) :as orch :include-macros true]))

(defn orch-instrument
  []
  (orch/instrument))

(def large-number
  #?(:clj  (dec (bigint (.. 2N toBigInteger (pow 53))))
     :cljs js/Number.MAX_SAFE_INTEGER))

(defn report-check-results
  [results]
  (doseq [result results]
    (let [{:keys [failure] :as abbrev}
          (stest/abbrev-result result)

          message
          (binding [s/*explain-out* expound/printer]
            (expound/explain-result-str result))

          expected
          (->> abbrev :spec rest (apply hash-map) :ret)

          actual
          (if (instance? #?(:cljs js/Error :clj Throwable) failure)
            failure
            (::stest/val failure))]

      (t/do-report {:type     (if failure :fail :pass)
                    :message  message
                    :expected expected
                    :actual   actual}))))
