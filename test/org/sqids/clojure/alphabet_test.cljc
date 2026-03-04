(ns org.sqids.clojure.alphabet-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.alphabet :as alphabet])
  #?(:clj
     (:import
       (clojure.lang
         ExceptionInfo))))

(defn make
  "Builds a Sqids config for a custom alphabet."
  [alphabet]
  (sut/sqids {:alphabet alphabet}))

(defn alphabet-spec-fails
  "Asserts alphabet initialization fails with the expected root spec."
  [alphabet root-spec]
  (let [e
        (t/is (thrown? ExceptionInfo (make alphabet)))

        {::s/keys [problems]}
        (ex-data e)]

    (t/is (seq problems))
    (t/is (some (fn [{:keys [via] value :val}]
                  (and (= alphabet value)
                       (= root-spec (last via))))
                problems))))

(t/deftest simple-alphabet-test
  (let [sqids   (make "0123456789abcdef")
        numbers [1 2 3]
        id      "489158"]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest short-alphabet-test
  (let [sqids   (make "abc")
        numbers [1 2 3]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest long-alphabet-test
  (let [sqids   (make "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_+|{}[];:'\"/?.>,<`~")
        numbers [1 2 3]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest multibyte-tests
  (alphabet-spec-fails "ë1092" ::alphabet/alphabet-no-multibyte))

(t/deftest repeating-alphabet-characters
  (alphabet-spec-fails "aabcdefg" ::alphabet/alphabet-distinct))

(t/deftest too-short-of-an-alphabet
  (alphabet-spec-fails "ab" ::alphabet/alphabet-min-length))
