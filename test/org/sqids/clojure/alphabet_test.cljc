(ns org.sqids.clojure.alphabet-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t :refer [deftest is]]
    [clojure.test.check.generators]
    [clojure.test.check.properties]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.test-util :as u]))

(u/orch-instrument)

(defn make
  [alphabet]
  (sut/sqids {:alphabet alphabet}))

(deftest simple-alphabet-test
  (let [sqids   (make "0123456789abcdef")
        numbers [1 2 3]
        id      "489158"]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))

(deftest short-alphabet-test
  (let [sqids   (make "abc")
        numbers [1 2 3]]
    (is (= numbers (->> numbers
                        (sut/encode sqids)
                        (sut/decode sqids))))))

(deftest wrong-type-test
  (is (not (s/valid? ::alphabet/alphabet false))))

(deftest multibyte-tests
  (let [alphabet "ë1092"]
    (is (not (s/valid? ::alphabet/alphabet alphabet)))
    (is (not (s/valid? ::alphabet/no-multibyte alphabet)))
    (is (s/valid? ::alphabet/distinct alphabet))
    (is (s/valid? ::alphabet/min-length alphabet))))

(deftest repeating-alphabet-characters
  (let [alphabet "aabcdefg"]
    (is (not (s/valid? ::alphabet/alphabet alphabet)))
    (is (s/valid? ::alphabet/no-multibyte alphabet))
    (is (not (s/valid? ::alphabet/distinct alphabet)))
    (is (s/valid? ::alphabet/min-length alphabet))))

(deftest too-short-of-an-alphabet
  (let [alphabet "ab"]
    (is (not (s/valid? ::alphabet/alphabet alphabet)))
    (is (s/valid? ::alphabet/no-multibyte alphabet))
    (is (s/valid? ::alphabet/distinct alphabet))
    (is (not (s/valid? ::alphabet/min-length alphabet)))))
