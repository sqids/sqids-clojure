(ns org.sqids.clojure-test
  (:require
    #?(:clj [clojure.edn :as edn]
       :cljs [cljs.reader :as edn])
    #?(:clj [clojure.java.io])
    [clojure.string :as str]
    [clojure.test :as t]
    [clojure.test.check :as check]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [org.sqids.clojure :as sut])
  #?(:clj
     (:import
       (java.util
         Locale))))

(t/deftest options-can-be-reused-and-serialized-test
  (doseq [options [sut/default-options
                   {:alphabet "abcdefg"
                    :min-length 12
                    :block-list #{"ABC" "ignore-this"}}]]
    (let [instance (sut/sqids options)
          id (sut/encode instance [1 2 3])]
      (doseq [saved-options [options (edn/read-string (pr-str options))]]
        (let [rebuilt (sut/sqids saved-options)]
          (t/is (= id (sut/encode rebuilt [1 2 3])))
          (t/is (= [1 2 3] (sut/decode rebuilt id))))))))

(t/deftest changed-options-are-applied-by-construction-test
  (let [options sut/default-options
        config (sut/sqids options)
        changed (sut/sqids (assoc options :block-list #{"86Rf07"}))]
    (t/is (= "se8ojk" (sut/encode changed [1 2 3])))
    (t/is (= "86Rf07" (sut/encode config [1 2 3])))
    (t/is (= [1 2 3] (sut/decode changed "se8ojk")))))

(t/deftest returned-instance-is-not-associative-test
  (let [instance (sut/sqids)]
    (t/is (false? (map? instance)))
    (t/is (false? (associative? instance)))
    (doseq [option-key [:min-length :instance]]
      (t/is (thrown? #?(:clj ClassCastException :cljs js/Error)
              (assoc instance option-key nil))))))

(t/deftest explicit-construction-is-required-test
  (doseq [config [nil {} sut/default-options {:instance {}} {:instance (sut/sqids)}]]
    (doseq [numbers [[] [1 2 3]]]
      (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
              (sut/encode config numbers))))
    (doseq [id ["" "86Rf07"]]
      (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
              (sut/decode config id))))))

(t/deftest invalid-options-test
  (doseq [options [nil [] "options" 123 (sut/sqids)
                   {:alphabet nil} {:min-length nil} {:block-list nil}
                   {:block-list []} {:block-list #{nil}} {:block-list #{1}}]]
    (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
            (sut/sqids options))
          (pr-str options))))

(def ^:private default-instance
  "Default Sqids instance used by encoding tests."
  (sut/sqids))

(t/deftest canonical-id-test
  (let [numbers [1 2 3]
        id      "86Rf07"]
    (t/is (= id (sut/encode default-instance numbers)))
    (t/is (= numbers (sut/decode default-instance id)))))

(t/deftest different-inputs-test
  (let [numbers [0 0 0 1 2 3 100 1000 100000 1000000 #?(:clj 9007199254740991N :cljs js/Number.MAX_SAFE_INTEGER)]]
    (t/is (= numbers (->> numbers
                          (sut/encode default-instance)
                          (sut/decode default-instance))))))

(t/deftest incremental-number-test
  (doseq [[id & numbers] [["bM" 0] ["Uk" 1] ["gb" 2] ["Ef" 3] ["Vq" 4]
                          ["uw" 5] ["OI" 6] ["AX" 7] ["p6" 8] ["nJ" 9]]]
    (t/is (= id (sut/encode default-instance numbers)))
    (t/is (= numbers (sut/decode default-instance id)))))

(t/deftest incremental-numbers-same-index-0-test
  (doseq [[id & numbers]
          [["SvIz" 0 0] ["n3qa" 0 1] ["tryF" 0 2] ["eg6q" 0 3] ["rSCF" 0 4]
           ["sR8x" 0 5] ["uY2M" 0 6] ["74dI" 0 7] ["30WX" 0 8] ["moxr" 0 9]]]
    (t/is (= id (sut/encode default-instance numbers)))
    (t/is (= numbers (sut/decode default-instance id)))))

(t/deftest incremental-numbers-same-index-1-test
  (doseq [[id & numbers]
          [["SvIz" 0 0] ["nWqP" 1 0] ["tSyw" 2 0] ["eX68" 3 0] ["rxCY" 4 0]
           ["sV8a" 5 0] ["uf2K" 6 0] ["7Cdk" 7 0] ["3aWP" 8 0] ["m2xn" 9 0]]]
    (t/is (= id (sut/encode default-instance numbers)))
    (t/is (= numbers (sut/decode default-instance id)))))

(t/deftest multi-input-test
  (let [numbers (range 0 100)]
    (t/is (= numbers (->> numbers
                          (sut/encode default-instance)
                          (sut/decode default-instance))))))

(t/deftest many-numbers-roundtrip-test
  (let [config (sut/sqids {:alphabet "abc" :block-list #{}})
        numbers (vec (repeat 4096 0))
        id (sut/encode config numbers)]
    (t/is (= 8192 (count id)))
    (t/is (= numbers (sut/decode config id)))))

(t/deftest encode-no-numbers-test
  (t/is (= "" (sut/encode default-instance []))))

(t/deftest decode-empty-string-test
  (t/is (= [] (sut/decode default-instance ""))))

(t/deftest decode-invalid-character-test
  (t/is (= [] (sut/decode default-instance "*"))))

#?(:cljs
   (t/deftest decode-overflow-safe-integer-test
     ;; Encoded on CLJ from [9007199254740992N], which exceeds JS MAX_SAFE_INTEGER.
     (t/is (= [] (sut/decode default-instance "pup591lWlB")))))

(t/deftest invalid-numbers-test
  (doseq [numbers [nil 1 "123" #{1 2} {:a 1} [-1] [1.5] [##Inf] [##NaN]
                   [nil] [true] ["1"] [1 2 -3]]]
    (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
            (sut/encode default-instance numbers))
          (pr-str numbers)))
  #?(:cljs
     (t/is (thrown? cljs.core/ExceptionInfo
             (sut/encode default-instance [9007199254740992])))))

(t/deftest sequential-inputs-test
  (doseq [numbers [[1 2 3] '(1 2 3) (map inc (range 3))]]
    (t/is (= "86Rf07" (sut/encode default-instance numbers)))))

(t/deftest caller-exceptions-propagate-test
  (let [cause (ex-info "Caller sequence failed" {:input :failed})
        numbers (map (fn [_] (throw cause)) [1])
        caught (try
                 (sut/encode default-instance numbers)
                 nil
                 (catch #?(:clj Exception :cljs :default) error
                   error))]
    (t/is (identical? cause caught))))

#?(:clj
   (t/deftest big-integer-roundtrip-test
     (let [numbers [0N
                    1N
                    18446744073709551616N
                    340282366920938463463374607431768211455N
                    12345678901234567890123456789012345678901234567890N]
           id      (sut/encode default-instance numbers)]
       (t/is (string? id))
       (t/is (= numbers (sut/decode default-instance id))))))

#?(:clj
   (t/deftest shared-configuration-is-thread-safe-test
     (let [config (sut/sqids)
           jobs (doall
                  (repeatedly 8
                              #(future
                                 (every? (fn [_]
                                           (and (= "86Rf07" (sut/encode config [1 2 3]))
                                                (= [1 2 3] (sut/decode config "86Rf07"))))
                                         (range 100)))))]
       (t/is (every? deref jobs)))))

(t/deftest decode-cursor-boundaries-test
  (let [config (sut/sqids)]
    (doseq [[id expected] [["" []] ["8" []] ["0" []] ["00" [60]]
                           ["86Rf07" [1 2 3]]
                           ["86Rf07x" [1 2 3]]
                           ["86Rf07xd4z" [1 2 3]]]]
      (t/is (= expected (sut/decode config id)) id))))

(t/deftest invalid-characters-test
  (let [config (sut/sqids)]
    (doseq [id ["*" "*86Rf07" "86*Rf07" "86Rf07*" "86Rf07xd4z*" "😀" "ë"]]
      (t/is (= [] (sut/decode config id)) id))))

(t/deftest non-string-inputs-test
  (let [config (sut/sqids)]
    (doseq [id [nil 1 :id [\8 \6] true]]
      (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
              (sut/decode config id))
            (pr-str id)))))

(t/deftest javascript-safe-integer-boundary-test
  (let [config (sut/sqids)]
    ;; The second ID encodes MAX_SAFE_INTEGER + 1; JVM arithmetic retains it,
    ;; while JavaScript must reject it rather than silently round it.
    (t/is (= [9007199254740991] (sut/decode config (sut/encode config [9007199254740991]))))
    (t/is (= #?(:clj [9007199254740992N] :cljs [])
             (sut/decode config "pup591lWlB")))))

(t/deftest simple-alphabet-test
  (let [sqids   (sut/sqids {:alphabet "0123456789abcdef"})
        numbers [1 2 3]
        id      "489158"]
    (t/is (= id (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids id)))))

(t/deftest short-alphabet-test
  (let [sqids   (sut/sqids {:alphabet "abc"})
        numbers [1 2 3]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest long-alphabet-test
  (let [alphabet "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()-_+|{}[];:'\"/?.>,<`~"
        sqids (sut/sqids {:alphabet alphabet})
        numbers [1 2 3]]
    (t/is (= numbers (->> numbers
                          (sut/encode sqids)
                          (sut/decode sqids))))))

(t/deftest invalid-alphabets-test
  (doseq [alphabet [nil 123 [:a :b :c] "ë1092" "ab\u0080" "ab\ud800" "ab\udfff"
                    "abc😀" "aabcdefg" "" "a" "ab"]]
    (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
            (sut/sqids {:alphabet alphabet}))
          (pr-str alphabet))))

(t/deftest full-ascii-alphabet-test
  (let [alphabet (apply str (map char (range 128)))
        config (sut/sqids {:alphabet alphabet :block-list #{}})
        numbers [0 127 128 16384]]
    (t/is (= numbers (sut/decode config (sut/encode config numbers))))))

(def ^:private default-alphabet-length
  "Default alphabet length used in padding boundary tests."
  (count "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))

(def ^:private padded-instance
  "Sqids instance padded to the default alphabet length."
  (sut/sqids {:min-length default-alphabet-length}))

(t/deftest padded-id-test
  (let [numbers [1 2 3]
        id      "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTM"]
    (t/is (= id (sut/encode padded-instance numbers)))
    (t/is (= numbers (sut/decode padded-instance id)))))

(t/deftest incremental-min-length-test
  (let [numbers [1 2 3]]
    (doseq [[target-min-length expected-id]
            [[6 "86Rf07"]
             [7 "86Rf07x"]
             [8 "86Rf07xd"]
             [9 "86Rf07xd4"]
             [10 "86Rf07xd4z"]
             [11 "86Rf07xd4zB"]
             [12 "86Rf07xd4zBm"]
             [13 "86Rf07xd4zBmi"]
             [(+ default-alphabet-length 0) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTM"]
             [(inc default-alphabet-length) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMy"]
             [(+ default-alphabet-length 2) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMyf"]
             [(+ default-alphabet-length 3) "86Rf07xd4zBmiJXQG6otHEbew02c3PWsUOLZxADhCpKj7aVFv9I8RquYrNlSTMyf1"]]]
      (let [sqids-config (sut/sqids {:min-length target-min-length})]
        (t/is (= expected-id (sut/encode sqids-config numbers)))
        (t/is (= numbers (sut/decode sqids-config expected-id)))))))

(t/deftest padded-incremental-numbers-test
  (doseq [[id & numbers]
          [["SvIzsqYMyQwI3GWgJAe17URxX8V924Co0DaTZLtFjHriEn5bPhcSkfmvOslpBu" 0 0]
           ["n3qafPOLKdfHpuNw3M61r95svbeJGk7aAEgYn4WlSjXURmF8IDqZBy0CT2VxQc" 0 1]
           ["tryFJbWcFMiYPg8sASm51uIV93GXTnvRzyfLleh06CpodJD42B7OraKtkQNxUZ" 0 2]
           ["eg6ql0A3XmvPoCzMlB6DraNGcWSIy5VR8iYup2Qk4tjZFKe1hbwfgHdUTsnLqE" 0 3]
           ["rSCFlp0rB2inEljaRdxKt7FkIbODSf8wYgTsZM1HL9JzN35cyoqueUvVWCm4hX" 0 4]
           ["sR8xjC8WQkOwo74PnglH1YFdTI0eaf56RGVSitzbjuZ3shNUXBrqLxEJyAmKv2" 0 5]
           ["uY2MYFqCLpgx5XQcjdtZK286AwWV7IBGEfuS9yTmbJvkzoUPeYRHr4iDs3naN0" 0 6]
           ["74dID7X28VLQhBlnGmjZrec5wTA1fqpWtK4YkaoEIM9SRNiC3gUJH0OFvsPDdy" 0 7]
           ["30WXpesPhgKiEI5RHTY7xbB1GnytJvXOl2p0AcUjdF6waZDo9Qk8VLzMuWrqCS" 0 8]
           ["moxr3HqLAK0GsTND6jowfZz3SUx7cQ8aC54Pl1RbIvFXmEJuBMYVeW9yrdOtin" 0 9]]]
    (t/is (= id (sut/encode padded-instance numbers)))
    (t/is (= numbers (sut/decode padded-instance id)))))

(t/deftest min-lengths-test
  (doseq [target-min-length [0 1 5 10 default-alphabet-length]]
    (let [sqids-config (sut/sqids {:min-length target-min-length})]
      (doseq [numbers [[0]
                       [0 0 0 0 0]
                       [1 2 3 4 5 6 7 8 9 10]
                       [100 200 300]
                       [1000 2000 3000]
                       [1000000]
                       [(long #?(:clj Long/MAX_VALUE
                                 :cljs js/Number.MAX_SAFE_INTEGER))]]]
        (let [id (sut/encode sqids-config numbers)]
          (t/is (<= target-min-length (count id)))
          (t/is (= numbers (sut/decode sqids-config id))))))))

(t/deftest invalid-min-length-test
  (doseq [value [nil -1 256 1.5 "10" ##Inf ##NaN]]
    (t/is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
            (sut/sqids {:min-length value}))
          (pr-str value))))

(t/deftest maximum-min-length-test
  (let [config (sut/sqids {:alphabet "abc" :min-length 255 :block-list #{}})
        id (sut/encode config [1 2 3])]
    (t/is (= 255 (count id)))
    (t/is (= [1 2 3] (sut/decode config id)))
    (t/is (= "" (sut/encode config [])))))

#?(:clj
   (t/deftest integral-bigint-min-length-test
     (t/is (= "86Rf07xd4z" (sut/encode (sut/sqids {:min-length 10N}) [1 2 3])))))

(t/deftest default-block-list-test
  (let [sqids   (sut/sqids)
        numbers [4572721]]
    (t/is (= numbers (sut/decode sqids "aho1e")))
    (t/is (= "JExTR" (sut/encode sqids numbers)))))

(t/deftest empty-block-list-test
  (let [sqids   (sut/sqids {:block-list #{}})
        numbers [4572721]]
    (t/is (= numbers (sut/decode sqids "aho1e")))
    (t/is (= "aho1e" (sut/encode sqids numbers)))))

(t/deftest non-empty-block-list-test
  (let [sqids (sut/sqids {:block-list #{"ArUO"}})]
    (let [numbers [4572721]]
      (t/is (= numbers (sut/decode sqids "aho1e")))
      (t/is (= "aho1e" (sut/encode sqids numbers))))
    (let [numbers [100000]]
      (t/is (= numbers (sut/decode sqids "ArUO")))
      (t/is (= "QyG4" (sut/encode sqids numbers)))
      (t/is (= numbers (sut/decode sqids "QyG4"))))))

(t/deftest encode-block-list-test
  (let [sqids   (sut/sqids {:block-list #{"JSwXFaosAN" ; normal result of 1st encoding, block explicitly
                                          "OCjV9JK64o" ; result of 2nd encoding
                                          "rBHf" ; result of 3rd encoding is `4rBHfOiqd3`, block substring
                                          "79SM" ; result of 4th encoding is `dyhgw479SM`, block postfix
                                          "7tE6" ; result of 4th encoding is `7tE6jdAHLe`, block prefix
                                          }})
        numbers [1000000 2000000]]
    (t/is (= "1aYeB7bRUt" (sut/encode sqids numbers)))
    (t/is (= numbers (sut/decode sqids "1aYeB7bRUt")))))

(t/deftest decode-block-list-test
  (let [sqids   (sut/sqids {:block-list #{"86Rf07" "se8ojk" "ARsz1p" "Q8AI49" "5sQRZO"}})
        numbers [1 2 3]]
    (t/is (= numbers (sut/decode sqids "86Rf07")))
    (t/is (= numbers (sut/decode sqids "se8ojk")))
    (t/is (= numbers (sut/decode sqids "ARsz1p")))
    (t/is (= numbers (sut/decode sqids "Q8AI49")))
    (t/is (= numbers (sut/decode sqids "5sQRZO")))))

(t/deftest short-block-list-test
  (let [sqids   (sut/sqids {:block-list #{"pnd"}})
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

(t/deftest irrelevant-block-list-words-test
  (let [options {:alphabet "abc123" :block-list #{"AB1" "xy" "ab!" "B2C" "12a"}}
        config (sut/sqids options)
        normalized (sut/sqids (assoc options :block-list #{"ab1" "b2c" "12a"}))]
    (t/is (= #{"AB1" "xy" "ab!" "B2C" "12a"} (:block-list options)))
    (doseq [numbers [[0] [1 2 3] [1000 2000]]]
      (t/is (= (sut/encode normalized numbers) (sut/encode config numbers))))))

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
  (let [sqids (sut/sqids {:block-list #{"hey"}})]
    (t/is (= "86u" (sut/encode sqids [100]))))

  ;; id or word <= 3 chars should match exactly (blocked)
  (let [sqids (sut/sqids {:block-list #{"86u"}})]
    (t/is (= "sec" (sut/encode sqids [100]))))

  ;; short block-list words should not match inside longer ids
  (let [sqids (sut/sqids {:block-list #{"vFo"}})]
    (t/is (= "gMvFo" (sut/encode sqids [1000000]))))

  ;; word with digits should match prefix
  (let [sqids (sut/sqids {:block-list #{"lP3i"}})]
    (t/is (= "oDqljxrokxRt" (sut/encode sqids [100 202 303 404]))))

  ;; word with digits should match suffix
  (let [sqids (sut/sqids {:block-list #{"1HkYs"}})]
    (t/is (= "oDqljxrokxRt" (sut/encode sqids [100 202 303 404]))))

  ;; word with digits should not match in the middle
  (let [sqids (sut/sqids {:block-list #{"0hfxX"}})]
    (t/is (= "862REt0hfxXVdsLG8vGWD" (sut/encode sqids [101 202 303 404 505 606 707]))))

  ;; word without digits should match in the middle
  (let [sqids (sut/sqids {:block-list #{"hfxX"}})]
    (t/is (= "seu8n1jO9C4KQQDxdOxsK" (sut/encode sqids [101 202 303 404 505 606 707])))))

#?(:clj
   (t/deftest read-default-blocklist-missing-resource-test
     (with-redefs [clojure.java.io/resource (constantly nil)]
       (t/is (thrown-with-msg? clojure.lang.ExceptionInfo #"blocklist"
               (#'sut/read-default-blocklist nil nil))))))

#?(:clj
   (t/deftest block-list-matching-is-locale-independent-test
     (let [original-locale (Locale/getDefault)]
       (try
         (Locale/setDefault (Locale/forLanguageTag "tr-TR"))
         (let [config (sut/sqids {:block-list #{"LP3I"}})]
           (t/is (= "oDqljxrokxRt" (sut/encode config [100 202 303 404]))))
         (finally
           (Locale/setDefault original-locale))))))

(def ^:private number-gen
  "Random ordinary integers mixed with arithmetic boundary values."
  (gen/frequency
    [[4 (gen/choose 0 1000000000)]
     [1 (gen/elements #?(:clj [0 1 9007199254740991N 9223372036854775807N
                               9223372036854775808N 340282366920938463463374607431768211455N]
                         :cljs [0 1 9007199254740990 9007199254740991]))]]))

(def ^:private options-gen
  "Varied alphabet order, alphabet length and minimum length, without blocking."
  (gen/let [characters (gen/shuffle (vec (:alphabet sut/default-options)))
            length (gen/choose 3 (count characters))
            min-length (gen/choose 0 255)]
    {:alphabet (apply str (take length characters))
     :min-length min-length
     :block-list #{}}))

(t/deftest generated-roundtrips-test
  (let [result
        (check/quick-check
          400
          (prop/for-all [options options-gen
                         numbers (gen/vector number-gen 0 12)]
                        (let [config (sut/sqids options)
                              id (sut/encode config numbers)
                              decoded (sut/decode config id)]
                          (and (vector? decoded)
                               (= numbers decoded)
                               (= id (sut/encode config numbers))
                               (every? (set (:alphabet options)) id)
                               (if (empty? numbers)
                                 (= "" id)
                                 (<= (:min-length options) (count id))))))
          :seed 42000)]
    (t/is (:pass? result) (pr-str result))))

(t/deftest generated-blocklist-retries-test
  (let [config (sut/sqids {:block-list #{}})
        result
        (check/quick-check
          100
          (prop/for-all [number (gen/choose 10000 1000000000)]
                        (let [id (sut/encode config [number])
                              blocked-config (sut/sqids {:block-list #{(str/upper-case id)}})
                              retry-id (sut/encode blocked-config [number])]
                          (and (not= (str/lower-case id) (str/lower-case retry-id))
                               (= [number] (sut/decode blocked-config retry-id))
                               (= [number] (sut/decode blocked-config id)))))
          :seed 42001)]
    (t/is (:pass? result) (pr-str result))))
