(ns org.sqids.clojure.spec-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as stest]
    [clojure.test :as t :refer [deftest]]
    [clojure.test.check]
    [clojure.test.check.properties]
    [expound.alpha :as expound]
    [org.sqids.clojure]
    [org.sqids.clojure.spec]))

(defn check
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

(deftest sqids-test
  (check (stest/check `org.sqids.clojure/sqids)))

(deftest decode-test
  (check (stest/check `org.sqids.clojure/decode)))
