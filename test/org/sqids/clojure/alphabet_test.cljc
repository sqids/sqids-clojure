(ns org.sqids.clojure.alphabet-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t :refer [deftest is]]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.spec :as spec])
  #?(:clj
     (:import
       (clojure.lang
         ExceptionInfo))))

(defn make
  [alphabet]
  (sut/sqids {:alphabet alphabet}))

(defn alphabet-spec-fails
  [alphabet root-spec]
  (let [e
        (is (thrown? ExceptionInfo (make alphabet)))

        {::s/keys [problems]}
        (ex-data e)]

    (is (= 1 (count problems)))
    (let [{:keys [via val]} (first problems)]
      (is (= alphabet val))
      (is (= root-spec (last via))))))

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

(deftest multibyte-tests
  (alphabet-spec-fails "ë1092" ::spec/alphabet-no-multibyte))

(deftest repeating-alphabet-characters
  (alphabet-spec-fails "aabcdefg" ::spec/alphabet-distinct))

(deftest too-short-of-an-alphabet
  (alphabet-spec-fails "ab" ::spec/alphabet-min-length))
