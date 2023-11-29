(ns org.sqids.clojure.encoding-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is]]
    [org.sqids.clojure :as sut]))


(def sqids
  (sut/sqids))


(deftest simple-test
  (let [numbers [1 2 3]
        id      "86Rf07"]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))


(deftest different-inputs-test
  (let [numbers [0 0 0 1 2 3 100 1000 100000 1000000 Long/MAX_VALUE]]
    (is (= numbers (->> numbers
                        (sut/encode sqids)
                        (sut/decode sqids))))))


(deftest incremental-number-test
  (doseq [[id & numbers] [["bM" 0] ["Uk" 1] ["gb" 2] ["Ef" 3] ["Vq" 4]
                          ["uw" 5] ["OI" 6] ["AX" 7] ["p6" 8] ["nJ" 9]]]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))


(deftest incremental-numbers-test
  (doseq [[id & numbers]
          [["SvIz" 0 0] ["n3qa" 0 1] ["tryF" 0 2] ["eg6q" 0 3] ["rSCF" 0 4]
           ["sR8x" 0 5] ["uY2M" 0 6] ["74dI" 0 7] ["30WX" 0 8] ["moxr" 0 9]]]
    (is (= id (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids id)))))


(deftest multi-input-test
  (let [numbers (range 0 100)]
    (is (= numbers (->> numbers
                        (sut/encode sqids)
                        (sut/decode sqids))))))


(deftest encode-no-numbers-test
  (is (= "" (sut/encode sqids []))))


(deftest decode-empty-string-test
  (is (= [] (sut/decode sqids ""))))


(deftest decode-invalid-character-test
  (is (= [] (sut/decode sqids "*"))))


(defn nat-ints-spec-fails
  [number]
  (let [e
        (is (thrown? clojure.lang.ExceptionInfo (sut/encode sqids [number])))

        {::s/keys [problems]}
        (ex-data e)]

    (is (= 1 (count problems)))
    (let [{:keys [path via val]} (first problems)]
      (is (= [:nat-ints] path))
      (is (= number val))
      (is (= ::sut/nat-ints (last via))))))


(deftest encode-out-of-range-numbers-test
  (nat-ints-spec-fails -1)
  (nat-ints-spec-fails (+' Long/MAX_VALUE 1)))
