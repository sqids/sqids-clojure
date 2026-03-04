(ns org.sqids.clojure.invariants-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [org.sqids.clojure.decoding :as decoding]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.invariants :as invariants]
    [org.sqids.clojure.results :as results]))

(t/deftest encode-result-consistent-empty-numbers-test
  (t/is (true? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                      :min-length 5
                                                      :block-list #{}}
                                                     []
                                                     (results/ok "")))))

(t/deftest encode-result-consistent-valid-encode-test
  (let [config        (::results/value (init/sqids {:alphabet "abcde"}))
        encode-result (encoding/encode config [1 2 3])]
    (t/is (true? (invariants/encode-result-consistent? config
                                                       [1 2 3]
                                                       encode-result)))))

(t/deftest encode-result-consistent-rejects-invalid-config-test
  (t/is (false? (invariants/encode-result-consistent? {:alphabet "aa"}
                                                      [1]
                                                      (results/ok "abc")))))

(t/deftest encode-result-consistent-rejects-invalid-numbers-test
  (t/is (false? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                       :min-length 0
                                                       :block-list #{}}
                                                      [:bad]
                                                      (results/ok "abc")))))

(t/deftest encode-result-consistent-rejects-invalid-sqid-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"
                                             :block-list #{"cab"}}))]
    (t/is (false? (invariants/encode-result-consistent? config
                                                        [0]
                                                        (results/ok "cab"))))))

(t/deftest encode-result-consistent-rejects-empty-sqid-for-non-empty-input-test
  (t/is (false? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                       :min-length 0
                                                       :block-list #{}}
                                                      [1]
                                                      (results/ok "")))))

(t/deftest encode-result-consistent-rejects-out-of-alphabet-sqid-test
  (t/is (false? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                       :min-length 0
                                                       :block-list #{}}
                                                      [1]
                                                      (results/ok "zzz")))))

(t/deftest encode-result-consistent-rejects-too-short-sqid-test
  (t/is (false? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                       :min-length 4
                                                       :block-list #{}}
                                                      [1]
                                                      (results/ok "abc")))))

(t/deftest encode-result-consistent-rejects-failed-roundtrip-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"}))]
    (with-redefs [decoding/decode (fn [_ _]
                                    (results/error ::results/unexpected-exception
                                                   :stage
                                                   "boom"
                                                   {}))]
      (t/is (false? (invariants/encode-result-consistent? config
                                                          [1]
                                                          (results/ok "abc")))))))

(t/deftest encode-result-consistent-rejects-invalid-input-errors-for-valid-args-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"}))]
    (t/is (false? (invariants/encode-result-consistent? config
                                                        [1]
                                                        (results/invalid-input :stage
                                                                               [1]
                                                                               {::s/problems []}))))))

(t/deftest encode-result-consistent-allows-non-input-errors-for-valid-args-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"}))]
    (t/is (true? (invariants/encode-result-consistent? config
                                                       [1]
                                                       (results/encode-max-attempts :stage
                                                                                    {:increment 1
                                                                                     :numbers [1]}))))))

(t/deftest encode-result-consistent-allows-invalid-input-errors-for-invalid-args-test
  (t/is (true? (invariants/encode-result-consistent? {:alphabet "abcde"
                                                      :min-length 0
                                                      :block-list #{}}
                                                     [:bad]
                                                     (results/invalid-input :stage
                                                                            [:bad]
                                                                            {::s/problems []})))))

(t/deftest decode-result-consistent-valid-decode-test
  (let [config        (::results/value (init/sqids {:alphabet "abcde"}))
        encode-result (encoding/encode config [1 2 3])
        sqid          (::results/value encode-result)
        decode-result (decoding/decode config sqid)]
    (t/is (true? (invariants/decode-result-consistent? config
                                                       sqid
                                                       decode-result)))))

(t/deftest decode-result-consistent-empty-and-invalid-character-sqids-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"}))]
    (t/is (true? (invariants/decode-result-consistent? config
                                                       ""
                                                       (results/ok []))))
    (t/is (true? (invariants/decode-result-consistent? config
                                                       "zzz"
                                                       (results/ok []))))
    (t/is (false? (invariants/decode-result-consistent? config
                                                        "zzz"
                                                        (results/ok [1]))))))

(t/deftest decode-result-consistent-allows-non-canonical-sqids-test
  (let [config (::results/value (init/sqids {}))]
    (t/is (true? (invariants/decode-result-consistent? config
                                                       "0"
                                                       (results/ok []))))
    (t/is (true? (invariants/decode-result-consistent? config
                                                       "00"
                                                       (results/ok [60]))))))

(t/deftest decode-result-consistent-rejects-invalid-config-and-non-string-successes-test
  (t/is (false? (invariants/decode-result-consistent? {:alphabet "aa"}
                                                      "abc"
                                                      (results/ok []))))
  (t/is (false? (invariants/decode-result-consistent? {:alphabet "abcde"
                                                       :min-length 0
                                                       :block-list #{}}
                                                      :not-a-string
                                                      (results/ok [])))))

(t/deftest decode-result-consistent-error-shape-test
  (let [config (::results/value (init/sqids {:alphabet "abcde"}))]
    (t/is (false? (invariants/decode-result-consistent? config
                                                        "abc"
                                                        (results/error ::results/unexpected-exception
                                                                       :stage
                                                                       "boom"
                                                                       {}))))
    (t/is (true? (invariants/decode-result-consistent? {:alphabet "aa"}
                                                       "abc"
                                                       (results/error ::results/unexpected-exception
                                                                      :stage
                                                                      "boom"
                                                                      {}))))
    (t/is (true? (invariants/decode-result-consistent? config
                                                       :not-a-string
                                                       (results/error ::results/unexpected-exception
                                                                      :stage
                                                                      "boom"
                                                                      {}))))))
