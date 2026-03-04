(ns org.sqids.clojure.platform-test
  (:require
    [clojure.test :as t]
    [org.sqids.clojure.platform :as platform]))

(t/deftest decode-overflow-sqid-predicate-test
  (t/is (= (some? platform/decode-overflow-sqid)
           (platform/decode-overflow-sqid-available?)))
  (t/is (false? (platform/decode-overflow-sqid? "not-overflow")))
  (with-redefs [platform/decode-overflow-sqid "fixture"]
    (t/is (true? (platform/decode-overflow-sqid-available?)))
    (t/is (true? (platform/decode-overflow-sqid? "fixture")))
    (t/is (false? (platform/decode-overflow-sqid? "other")))))
