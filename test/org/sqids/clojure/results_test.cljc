(ns org.sqids.clojure.results-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [org.sqids.clojure :as sut]
    [org.sqids.clojure.decoding :as decoding]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.results :as results]))

(defn namespaced-results-keys?
  "Returns true when all top-level keys in `result` use the results namespace."
  [result]
  (and (map? result)
       (every? qualified-keyword? (keys result))
       (every? #(= results/result-key-namespace (namespace %)) (keys result))))

(defn assert-results-map-shape
  "Asserts common structural guarantees for all result maps."
  [result]
  (t/is (map? result))
  (t/is (contains? result ::results/status))
  (t/is (namespaced-results-keys? result)))

(defn assert-error-shape
  "Asserts common structural guarantees for error result maps."
  [result expected-code]
  (assert-results-map-shape result)
  (t/is (= ::results/error (::results/status result)))
  (t/is (= expected-code (::results/code result)))
  (t/is (keyword? (::results/stage result)))
  (t/is (string? (::results/message result)))
  (t/is (map? (::results/details result))))

(defn thrown-message
  "Returns the platform-specific exception message string."
  [error]
  #?(:clj (.getMessage error)
     :cljs (ex-message error)))

(t/deftest namespaced-results-keys-rejects-non-map-test
  (t/is (false? (results/namespaced-results-keys? :not-a-map)))
  (t/is (false? (results/namespaced-results-keys? {:plain :key})))
  (t/is (false? (results/namespaced-results-keys? {:other.ns/key :value})))
  (t/is (true? (results/namespaced-results-keys? {::results/status ::results/ok}))))

(t/deftest result-shape-multispec-branches-test
  (let [exception            (ex-info "cause" {:source :shape-test})
        valid-ok             {::results/status ::results/ok
                              ::results/value  1}
        valid-unexpected     {::results/status    ::results/error
                              ::results/code      ::results/unexpected-exception
                              ::results/stage     ::results/test-stage
                              ::results/message   "boom"
                              ::results/details   {}
                              ::results/exception exception}
        valid-invalid-input  {::results/status   ::results/error
                              ::results/code     ::results/invalid-input
                              ::results/stage    ::results/test-stage
                              ::results/message  "bad args"
                              ::results/details  {}
                              ::results/input    :value
                              ::results/problems []}
        valid-max-attempts   {::results/status    ::results/error
                              ::results/code      ::results/encode-max-attempts
                              ::results/stage     ::results/test-stage
                              ::results/message   "retry"
                              ::results/details   {}
                              ::results/increment 1
                              ::results/numbers   [1]}]
    (t/is (false? (s/valid? ::results/ok-result-shape :not-a-map)))
    (t/is (false? (s/valid? ::results/ok-result-shape {:plain :key})))
    (t/is (false? (s/valid? ::results/ok-result-shape
                            {::results/status ::results/error ::results/value 1})))
    (t/is (false? (s/valid? ::results/ok-result-shape {::results/status ::results/ok})))
    (t/is (false? (s/valid? ::results/ok-result-shape
                            {::results/status ::results/ok
                             ::results/value  1
                             ::results/extra  true})))
    (t/is (true? (s/valid? ::results/ok-result-shape valid-ok)))

    (t/is (false? (s/valid? ::results/error-result-shape :not-a-map)))
    (t/is (false? (s/valid? ::results/error-result-shape {:plain :key})))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (assoc valid-unexpected ::results/status ::results/ok))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (dissoc valid-unexpected ::results/code))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (dissoc valid-unexpected ::results/stage))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (dissoc valid-unexpected ::results/message))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (dissoc valid-unexpected ::results/details))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (dissoc valid-unexpected ::results/exception))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (assoc valid-unexpected ::results/code ::results/invalid-input))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (assoc valid-unexpected ::results/code ::results/encode-max-attempts))))
    (t/is (false? (s/valid? ::results/error-result-shape
                            (assoc valid-unexpected ::results/code ::results/unknown-code))))
    (t/is (true? (s/valid? ::results/error-result-shape valid-unexpected)))
    (t/is (true? (s/valid? ::results/error-result-shape valid-invalid-input)))
    (t/is (true? (s/valid? ::results/error-result-shape valid-max-attempts)))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-unexpected ::results/stage "not-a-keyword"))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-unexpected ::results/message :not-a-string))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-unexpected ::results/details :not-a-map))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-unexpected ::results/exception nil))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-invalid-input ::results/problems :not-a-vector))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-max-attempts ::results/increment "not-an-int"))))
    (t/is (false? (s/valid? ::results/error-base-shape
                            (assoc valid-max-attempts ::results/numbers 42))))
    (t/is (true? (s/valid? ::results/error-base-shape valid-unexpected)))
    (t/is (true? (s/valid? ::results/error-base-shape valid-invalid-input)))
    (t/is (true? (s/valid? ::results/error-base-shape valid-max-attempts)))

    (t/is (false? (s/valid? ::results/result :not-a-map)))
    (t/is (false? (s/valid? ::results/result {::results/value 1})))
    (t/is (false? (s/valid? ::results/result
                            {::results/status ::results/unknown-status
                             ::results/value  1})))
    (t/is (false? (s/valid? ::results/result
                            {:other.ns/status ::results/ok
                             ::results/value  1})))
    (t/is (true? (s/valid? ::results/result valid-ok)))
    (t/is (true? (s/valid? ::results/result valid-unexpected)))))

(t/deftest invalid-input-helper-shape-test
  (let [input      {:fn :encode :args [nil [1 2 3]]}
        explain    {::s/problems [{:path [:args 0] :pred `map? :val nil}]
                    ::s/value    input}
        cause      (ex-info "Invalid input" explain)
        from-cause (results/invalid-input cause)
        explicit   (results/invalid-input ::results/validation input explain)]
    (doseq [result [from-cause explicit]]
      (assert-error-shape result ::results/invalid-input)
      (t/is (= input (::results/input result)))
      (t/is (vector? (::results/problems result))))))

(t/deftest conform-invalid-input-uses-spec-explain-message-test
  (let [result (results/conform ::init/options
                                {:alphabet "ab"}
                                ::results/test-stage)]
    (assert-error-shape result ::results/invalid-input)
    (t/is (not= "Invalid input" (::results/message result)))
    (t/is (re-find #"ab" (::results/message result)))))

(t/deftest encode-max-attempts-helper-shape-test
  (let [cause  (ex-info "Reached max attempts to re-generate the ID"
                        {:increment 63
                         :numbers   [0 1 2]})
        result (results/encode-max-attempts cause)]
    (assert-error-shape result ::results/encode-max-attempts)
    (t/is (= 63 (::results/increment result)))
    (t/is (= [0 1 2] (::results/numbers result)))))

(t/deftest unexpected-captures-original-exception-test
  (let [cause  (ex-info "unexpected" {:source :results-test})
        result (results/unexpected cause)]
    (assert-error-shape result ::results/unexpected-exception)
    (t/is (identical? cause (::results/exception result)))))

(t/deftest error-normalizes-non-map-details-test
  (let [result (results/error ::results/invalid-input
                              ::results/test-stage
                              "Invalid input"
                              42)]
    (assert-error-shape result ::results/invalid-input)
    (t/is (= {:value 42} (::results/details result)))))

(t/deftest unexpected-with-nil-message-falls-back-to-string-test
  (let [cause  #?(:clj (Exception.) :cljs (js/Error.))
        result (results/unexpected cause)]
    (assert-error-shape result ::results/unexpected-exception)))

(t/deftest bind-first-arity-test
  (let [result (results/bind (results/ok 1)
                             (fn [value]
                               (results/ok (inc value))))]
    (assert-results-map-shape result)
    (t/is (= ::results/ok (::results/status result)))
    (t/is (= 2 (::results/value result)))))

(t/deftest bind-catches-exceptions-test
  (let [cause  (ex-info "bind failure" {:source :bind-test})
        result (results/bind (results/ok :x)
                             ::results/test-stage
                             (fn [_]
                               (throw cause)))]
    (assert-error-shape result ::results/unexpected-exception)
    (t/is (identical? cause (::results/exception result)))))

(t/deftest conform-catches-spec-resolution-errors-test
  (let [result (results/conform ::results/missing-spec
                                :value
                                ::results/test-stage)]
    (assert-error-shape result ::results/unexpected-exception)))

(t/deftest init-sqids-invalid-input-does-not-throw-test
  (let [result (init/sqids {:alphabet "ab"})]
    (assert-error-shape result ::results/invalid-input)
    (t/is (seq (::results/problems result)))))

(t/deftest encoding-encode-max-attempts-does-not-throw-test
  (let [sqids-config (sut/sqids {:alphabet   "abc"
                                 :min-length 3
                                 :block-list #{"cab" "abc" "bca"}})
        result       (encoding/encode sqids-config [0])]
    (assert-error-shape result ::results/encode-max-attempts)
    (t/is (= 4 (::results/increment result)))
    (t/is (= [0] (::results/numbers result)))))

(t/deftest encoding-unexpected-path-preserves-exception-test
  (let [cause (ex-info "boom" {:source :with-redefs})]
    (with-redefs [init/ensure-initialized (fn [_] (throw cause))]
      (let [result (encoding/encode {:alphabet "abc"} [1])]
        (assert-error-shape result ::results/unexpected-exception)
        (t/is (identical? cause (::results/exception result)))))))

(t/deftest internal-success-shape-test
  (let [sqids-result  (init/sqids {})
        sqids-value  (::results/value sqids-result)
        encode-result (encoding/encode sqids-value [1 2 3])
        decode-result (decoding/decode sqids-value (::results/value encode-result))]
    (doseq [result [sqids-result encode-result decode-result]]
      (assert-results-map-shape result)
      (t/is (= ::results/ok (::results/status result))))
    (t/is (= [1 2 3] (::results/value decode-result)))))

(t/deftest public-api-still-throws-at-edge-test
  (let [sqids-config (sut/sqids {:alphabet   "abc"
                                 :min-length 3
                                 :block-list #{"cab" "abc" "bca"}})]
    #?(:clj
       (t/is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Reached max attempts to re-generate the ID"
               (sut/encode sqids-config [0])))
       :cljs
       (t/is (thrown-with-msg?
               js/Error
               #"Reached max attempts to re-generate the ID"
               (sut/encode sqids-config [0]))))))

(t/deftest public-sqids-invalid-input-throws-at-edge-test
  (let [result           (init/sqids {:alphabet "ab"})
        expected-message (::results/message result)]
    (assert-error-shape result ::results/invalid-input)
    (t/is (not= "Invalid input" expected-message))
    (try
      (sut/sqids {:alphabet "ab"})
      (t/is false "Expected sut/sqids to throw")
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
        (t/is (= expected-message (thrown-message error)))))))

(t/deftest public-decode-invalid-config-throws-at-edge-test
  (let [result           (decoding/decode {:alphabet "ab"} "abc")
        expected-message (::results/message result)]
    (assert-error-shape result ::results/invalid-input)
    (t/is (not= "Invalid input" expected-message))
    (try
      (sut/decode {:alphabet "ab"} "abc")
      (t/is false "Expected sut/decode to throw")
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
        (t/is (= expected-message (thrown-message error)))))))

#?(:clj
   (t/deftest public-edge-throw-preserves-cause-test
     (let [cause (Exception. "underlying failure")]
       (with-redefs [encoding/encode (fn [_ _]
                                       {::results/status    ::results/error
                                        ::results/code      ::results/unexpected-exception
                                        ::results/stage     ::results/test-stage
                                        ::results/message   "wrapper failure"
                                        ::results/details   {:source :wrapper-test}
                                        ::results/exception cause})]
         (try
           (sut/encode {} [1])
           (t/is false "Expected sut/encode to throw")
           (catch clojure.lang.ExceptionInfo error
             (t/is (= "wrapper failure" (.getMessage error)))
             (t/is (= {:source :wrapper-test} (ex-data error)))
             (t/is (identical? cause (.getCause error)))))))))

#?(:clj
   (t/deftest public-sqids-edge-throw-preserves-cause-test
     (let [cause (Exception. "sqids failure")]
       (with-redefs [init/sqids (fn [_]
                                  {::results/status    ::results/error
                                   ::results/code      ::results/unexpected-exception
                                   ::results/stage     ::results/test-stage
                                   ::results/message   "sqids failure"
                                   ::results/details   {:source :sqids-wrapper-test}
                                   ::results/exception cause})]
         (try
           (sut/sqids {})
           (t/is false "Expected sut/sqids to throw")
           (catch clojure.lang.ExceptionInfo error
             (t/is (= "sqids failure" (.getMessage error)))
             (t/is (= {:source :sqids-wrapper-test} (ex-data error)))
             (t/is (identical? cause (.getCause error)))))))))

#?(:clj
   (t/deftest public-decode-edge-throw-preserves-cause-test
     (let [cause (Exception. "decode failure")]
       (with-redefs [decoding/decode (fn [_ _]
                                       {::results/status    ::results/error
                                        ::results/code      ::results/unexpected-exception
                                        ::results/stage     ::results/test-stage
                                        ::results/message   "decode failure"
                                        ::results/details   {:source :decode-wrapper-test}
                                        ::results/exception cause})]
         (try
           (sut/decode {} "abc")
           (t/is false "Expected sut/decode to throw")
           (catch clojure.lang.ExceptionInfo error
             (t/is (= "decode failure" (.getMessage error)))
             (t/is (= {:source :decode-wrapper-test} (ex-data error)))
             (t/is (identical? cause (.getCause error)))))))))

(t/deftest public-edge-throw-defaults-message-and-details-test
  (with-redefs [encoding/encode (fn [_ _]
                                  {::results/status ::results/error
                                   ::results/code   ::results/unexpected-exception
                                   ::results/stage  ::results/test-stage})]
    (try
      (sut/encode {} [1])
      (t/is false "Expected sut/encode to throw")
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
        #?(:clj
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error))))
           :cljs
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error)))))))))

(t/deftest public-sqids-edge-throw-defaults-message-and-details-test
  (with-redefs [init/sqids (fn [_]
                             {::results/status ::results/error
                              ::results/code   ::results/unexpected-exception
                              ::results/stage  ::results/test-stage})]
    (try
      (sut/sqids {})
      (t/is false "Expected sut/sqids to throw")
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
        #?(:clj
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error))))
           :cljs
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error)))))))))

(t/deftest public-decode-edge-throw-defaults-message-and-details-test
  (with-redefs [decoding/decode (fn [_ _]
                                  {::results/status ::results/error
                                   ::results/code   ::results/unexpected-exception
                                   ::results/stage  ::results/test-stage})]
    (try
      (sut/decode {} "abc")
      (t/is false "Expected sut/decode to throw")
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) error
        #?(:clj
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error))))
           :cljs
           (do
             (t/is (= "Sqids operation failed" (thrown-message error)))
             (t/is (= {} (ex-data error)))))))))
