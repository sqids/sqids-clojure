(ns sqids-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [sqids :as sqids]))

(deftest roundtrip-test
  (is (= [1 2 3] (sqids/decode (sqids/encode [1 2 3])))))

(deftest string-reprentation-test
  (is (= "86Rf07" (sqids/encode [1 2 3]))))

(deftest with-new-engine
  (let [engine (sqids/build {:min-length 16 :alphabet "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"})
        input [1 2 3 8 9 10 11 12 13]
        encoded (sqids/encode engine input)]
    (is (= "sLiMkurjEvaLbbFqso" encoded))
    (is (= 18 (count encoded)))
    (is (= input (sqids/decode engine encoded)))))
