(ns org.sqids.clojure.block-list-test
  (:require
    #?@(:clj
        [[clojure.java.io]
         [org.sqids.clojure.block-list]])
    [clojure.test :as t]
    [org.sqids.clojure :as sut]))

(defn make
  "Builds a Sqids config with a custom block list."
  [block-list]
  (sut/sqids {:block-list block-list}))

(t/deftest default-block-list-test
  (let [sqids   (sut/sqids)
        numbers [4572721]]
    (t/is (= numbers (sut/decode sqids "aho1e")))
    (t/is (= "JExTR" (sut/encode sqids numbers)))))

(t/deftest empty-block-list-test
  (let [sqids   (make #{})
        numbers [4572721]]
    (t/is (= numbers (sut/decode sqids "aho1e")))
    (t/is (= "aho1e" (sut/encode sqids numbers)))))

(t/deftest non-empty-block-list-test
  (let [sqids (make #{"ArUO"})]
    (let [numbers [4572721]]
      (t/is (= numbers (sut/decode sqids "aho1e")))
      (t/is (= "aho1e" (sut/encode sqids numbers))))
    (let [numbers [100000]]
      (t/is (= numbers (sut/decode sqids "ArUO")))
      (t/is (= "QyG4" (sut/encode sqids numbers)))
      (t/is (= numbers (sut/decode sqids "QyG4"))))))

(t/deftest encode-block-list-test
  (let [sqids   (make #{"JSwXFaosAN" ; normal result of 1st encoding, block explicitly
                        "OCjV9JK64o" ; result of 2nd encoding
                        "rBHf" ; result of 3rd encoding is `4rBHfOiqd3`, block substring
                        "79SM" ; result of 4th encoding is `dyhgw479SM`, block postfix
                        "7tE6" ; result of 4th encoding is `7tE6jdAHLe`, block prefix
                        })
        numbers [1000000 2000000]]
    (t/is (= "1aYeB7bRUt" (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids "1aYeB7bRUt")))))

(t/deftest decode-block-list-test
  (let [sqids   (make #{"86Rf07" "se8ojk" "ARsz1p" "Q8AI49" "5sQRZO"})
        numbers [1 2 3]]
    (t/is (= numbers (sut/decode sqids "86Rf07")))
    (t/is (= numbers (sut/decode sqids "se8ojk")))
    (t/is (= numbers (sut/decode sqids "ARsz1p")))
    (t/is (= numbers (sut/decode sqids "Q8AI49")))
    (t/is (= numbers (sut/decode sqids "5sQRZO")))))

(t/deftest short-block-list-test
  (let [sqids   (make #{"pnd"})
        numbers [1000]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest lowercase-block-list-test
  (let [sqids   (sut/sqids {:alphabet   "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                            :block-list #{"sxnzkl"}})
        numbers [1 2 3]]
    (t/is (= "IBSHOZ" (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids "IBSHOZ")))))

(t/deftest block-list-normalization-test
  (let [sqids (sut/sqids {:alphabet   "abc123"
                          :block-list #{"AB1" "xy" "ab!" "B2C" "12a"}})]
    (t/is (= #{"ab1" "b2c" "12a"} (:block-list sqids)))))

(t/deftest raw-config-map-initialization-test
  (let [raw-config {:alphabet "abc"
                    :min-length 3
                    :block-list #{"cab"}}]
    (t/is (= "abc" (sut/encode raw-config [0])))
    (t/is (= [0] (sut/decode raw-config "abc")))))

(t/deftest max-block-list-test
  (let [sqids (sut/sqids {:alphabet "abc"
                          :min-length 3
                          :block-list #{"cab" "abc" "bca"}})]
    #?(:clj
       (t/is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Reached max attempts to re-generate the ID"
               (sut/encode sqids [0])))
       :cljs
       (t/is (thrown-with-msg? js/Error
                               #"Reached max attempts to re-generate the ID"
               (sut/encode sqids [0]))))))

(t/deftest specific-is-blocked-id-scenarios-test
  ;; id or word <= 3 chars should match exactly
  (let [sqids (make #{"hey"})]
    (t/is (= "86u" (sut/encode sqids [100]))))

  ;; id or word <= 3 chars should match exactly (blocked)
  (let [sqids (make #{"86u"})]
    (t/is (= "sec" (sut/encode sqids [100]))))

  ;; short block-list words should not match inside longer ids
  (let [sqids (make #{"vFo"})]
    (t/is (= "gMvFo" (sut/encode sqids [1000000]))))

  ;; word with digits should match prefix
  (let [sqids (make #{"lP3i"})]
    (t/is (= "oDqljxrokxRt" (sut/encode sqids [100 202 303 404]))))

  ;; word with digits should match suffix
  (let [sqids (make #{"1HkYs"})]
    (t/is (= "oDqljxrokxRt" (sut/encode sqids [100 202 303 404]))))

  ;; word with digits should not match in the middle
  (let [sqids (make #{"0hfxX"})]
    (t/is (= "862REt0hfxXVdsLG8vGWD" (sut/encode sqids [101 202 303 404 505 606 707]))))

  ;; word without digits should match in the middle
  (let [sqids (make #{"hfxX"})]
    (t/is (= "seu8n1jO9C4KQQDxdOxsK" (sut/encode sqids [101 202 303 404 505 606 707])))))

#?(:clj
   (t/deftest read-default-missing-resource-test
     (with-redefs [clojure.java.io/resource (constantly nil)]
       (t/is (= #{}
                (#'org.sqids.clojure.block-list/read-default nil nil))))))
