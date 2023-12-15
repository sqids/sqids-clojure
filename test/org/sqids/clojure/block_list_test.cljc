(ns org.sqids.clojure.block-list-test
  (:require
    [clojure.test :as t :refer [deftest is]]
    [clojure.test.check.generators]
    [clojure.test.check.properties]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.test-util :as u])
  #?(:clj
     (:import
       (clojure.lang
         ExceptionInfo))))

(u/orch-instrument)

(defn make
  [block-list]
  (sut/sqids {:block-list block-list}))

(deftest block-list-test
  (let [sqids   (sut/sqids)
        numbers [4572721]]
    (is (= numbers (sut/decode sqids "aho1e")))
    (is (= "JExTR" (sut/encode sqids numbers)))))

(deftest empty-block-list-test
  (let [sqids   (make #{})
        numbers [4572721]]
    (is (= numbers (sut/decode sqids "aho1e")))
    (is (= "aho1e" (sut/encode sqids numbers)))))

(deftest non-empty-block-list-test
  (let [sqids (make #{"ArUO"})]
    (let [numbers [4572721]]
      (is (= numbers (sut/decode sqids "aho1e"))
          (= "aho1e" (sut/encode sqids numbers))))
    (let [numbers [100000]]
      (is (= numbers (sut/decode sqids "ArUO")))
      (is (= "QyG4" (sut/encode sqids numbers)))
      (is (= numbers (sut/decode sqids "QyG4"))))))

(deftest encode-block-list
  (let [sqids   (make #{"JSwXFaosAN" ; normal result of 1st encoding, let's block that word on purpose
                        "OCjV9JK64o" ; result of 2nd encoding
                        "rBHf" ; result of 3rd encoding is `4rBHfOiqd3`, let's block a substring
                        "79SM" ; result of 4th encoding is `dyhgw479SM`, let's block the postfix
                        "7tE6" ; result of 4th encoding is `7tE6jdAHLe`, let's block the prefix
                        })
        numbers [1000000 2000000]]
    (is (= "1aYeB7bRUt" (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids "1aYeB7bRUt")))))

(deftest decode-block-list
  (let [sqids   (make #{"86Rf07" "se8ojk" "ARsz1p" "Q8AI49" "5sQRZO"})
        numbers [1 2 3]]
    (is (= numbers (sut/decode sqids "86Rf07")))
    (is (= numbers (sut/decode sqids "se8ojk")))
    (is (= numbers (sut/decode sqids "ARsz1p")))
    (is (= numbers (sut/decode sqids "Q8AI49")))
    (is (= numbers (sut/decode sqids "5sQRZO")))))

(deftest short-block-list
  (let [sqids   (make #{"pnd"})
        numbers [1000]]
    (is (= numbers (->> numbers
                        (sut/encode sqids)
                        (sut/decode sqids))))))

(deftest lowercase-block-list
  (let [sqids   (sut/sqids {:alphabet   "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                            :block-list #{"sxnzkl"}})
        numbers [1 2 3]]
    (is (= "IBSHOZ" (sut/encode sqids numbers)))
    (is (= numbers (sut/decode sqids "IBSHOZ")))))

(deftest max-block-list
  (let [sqids (sut/sqids {:alphabet   "abc"
                          :min-length 3
                          :block-list #{"cab" "abc" "bca"}})]
    (is (thrown? ExceptionInfo (sut/encode sqids [0])))))
