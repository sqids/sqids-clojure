(ns org.sqids.clojure.fdef-test
  (:require
    [clojure.spec.test.alpha :as stest]
    [clojure.test :as t :refer [deftest testing]]
    [clojure.test.check.generators]
    [clojure.test.check.properties]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.spec]
    [org.sqids.clojure.test-util :as u]))

(u/orch-instrument)

(deftest sqids-fdef-check
  (testing `sut/sqids
    (u/report-check-results (stest/check `sut/sqids))))

(deftest decode-fdef-check
  (testing `sut/decode
    (u/report-check-results (stest/check `sut/decode))))

;; It's possible for encoding to fail with an exception after too
;; many attempts to generate an ID, so generative testing is disabled.
;; TODO: Encode exceptions in the result so that throwing can be done
;; when unwrapping the result.
#_(deftest encode-fdef-check
  (testing `sut/encode
    (u/report-check-results (stest/check `sut/encode))))
