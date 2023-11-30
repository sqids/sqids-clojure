(ns org.sqids.clojure.alphabet-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is]]
    [org.sqids.clojure :as sut]))

(defn make
  [alphabet]
  (sut/sqids {:alphabet alphabet}))

(defn alphabet-spec-fails
  [alphabet root-spec]
  (let [e
        (is (thrown? clojure.lang.ExceptionInfo (make alphabet)))

        {::s/keys [problems]}
        (ex-data e)]

    (is (= 1 (count problems)))
    (let [{:keys [path via val]} (first problems)]
      (is (= [:unary :options :alphabet] path))
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
  (alphabet-spec-fails "ë1092" ::sut/alphabet-no-multibyte))

(deftest repeating-alphabet-characters
  (alphabet-spec-fails "aabcdefg" ::sut/alphabet-distinct))

(deftest too-short-of-an-alphabet
  (alphabet-spec-fails "ab" ::sut/alphabet-min-length))
