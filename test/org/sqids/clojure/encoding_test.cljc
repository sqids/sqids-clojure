(ns org.sqids.clojure.encoding-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [org.sqids.clojure :as sut]
    #?@(:clj [[org.sqids.clojure.decoding :as decoding]])
    [org.sqids.clojure.encoding :as encoding]
    #?(:cljs [org.sqids.clojure.platform :as platform]))
  #?(:clj
     (:import
       (clojure.lang
         ExceptionInfo))))

(def sqids
  "Default Sqids instance used by encoding tests."
  (sut/sqids))

(t/deftest simple-test
  (let [numbers [1 2 3]
        id      "86Rf07"]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest different-inputs-test
  (let [numbers [0 0 0 1 2 3 100 1000 100000 1000000 #?(:clj 9007199254740991N :cljs js/Number.MAX_SAFE_INTEGER)]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest incremental-number-test
  (doseq [[id & numbers] [["bM" 0] ["Uk" 1] ["gb" 2] ["Ef" 3] ["Vq" 4]
                          ["uw" 5] ["OI" 6] ["AX" 7] ["p6" 8] ["nJ" 9]]]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest incremental-numbers-same-index-0-test
  (doseq [[id & numbers]
          [["SvIz" 0 0] ["n3qa" 0 1] ["tryF" 0 2] ["eg6q" 0 3] ["rSCF" 0 4]
           ["sR8x" 0 5] ["uY2M" 0 6] ["74dI" 0 7] ["30WX" 0 8] ["moxr" 0 9]]]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest incremental-numbers-same-index-1-test
  (doseq [[id & numbers]
          [["SvIz" 0 0] ["nWqP" 1 0] ["tSyw" 2 0] ["eX68" 3 0] ["rxCY" 4 0]
           ["sV8a" 5 0] ["uf2K" 6 0] ["7Cdk" 7 0] ["3aWP" 8 0] ["m2xn" 9 0]]]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest multi-input-test
  (let [numbers (range 0 100)]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest encode-no-numbers-test
  (t/is (= "" (sut/encode sqids []))))

(t/deftest decode-empty-string-test
  (t/is (= [] (sut/decode sqids ""))))

(t/deftest decode-invalid-character-test
  (t/is (= [] (sut/decode sqids "*"))))

#?(:clj
   (t/deftest decode-defensive-nil-branch-test
     ;; Force internal number parsing failure to exercise the defensive []
     ;; branch in decoding/decode.
     (with-redefs [decoding/to-number (constantly nil)]
       (t/is (= [] (sut/decode sqids "86Rf07"))))))

#?(:cljs
   (t/deftest decode-overflow-safe-integer-test
     ;; Encoded on CLJ from [9007199254740992N], which exceeds JS MAX_SAFE_INTEGER.
     (t/is (= [] (sut/decode sqids "pup591lWlB")))))

(defn nat-ints-spec-fails
  "Asserts `encode` fails spec validation for an invalid integer."
  [number]
  (let [e
        (t/is (thrown? ExceptionInfo (sut/encode sqids [number])))

        {::s/keys [problems]}
        (ex-data e)]

    (t/is (seq problems))
    (t/is (some (fn [{:keys [via] value :val}]
                  (and (= number value)
                       (contains? #{::encoding/nat-ints ::encoding/nat-int} (last via))))
                problems))))

(t/deftest encode-out-of-range-numbers-test
  (nat-ints-spec-fails -1)
  #?(:cljs (nat-ints-spec-fails (inc platform/max-value))))

#?(:clj
   (t/deftest big-integer-roundtrip-test
     (let [numbers [0N
                    1N
                    18446744073709551616N
                    340282366920938463463374607431768211455N
                    12345678901234567890123456789012345678901234567890N]
           id      (sut/encode sqids numbers)]
       (t/is (string? id))
       (t/is (= numbers (sut/decode sqids id))))))
