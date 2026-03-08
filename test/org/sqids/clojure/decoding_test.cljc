(ns org.sqids.clojure.decoding-test
  (:require
    #?(:clj [clojure.spec.alpha :as s])
    #?(:clj [clojure.spec.gen.alpha :as gen])
    [clojure.test :as t]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.decoding :as decoding]
    #?(:clj [org.sqids.clojure.encoding :as encoding])
    #?(:clj [org.sqids.clojure.generators.decoding :as decoding-generators])
    #?(:clj [org.sqids.clojure.init :as init])
    [org.sqids.clojure.platform :as platform]
    #?(:clj [org.sqids.clojure.results :as results])))

(t/deftest to-number-empty-input-test
  (t/is (= 0 (#'decoding/to-number "" "abc"))))

(t/deftest to-number-invalid-character-test
  (t/is (nil? (#'decoding/to-number "z" "abc"))))

(t/deftest to-number-valid-input-test
  (t/is (= 0 (#'decoding/to-number "a" "abc")))
  (t/is (= 2 (#'decoding/to-number "c" "abc")))
  (t/is (= 5 (#'decoding/to-number "bc" "abc"))))

#?(:cljs
   (t/deftest to-number-out-of-range-test
     (with-redefs [platform/in-range? (constantly false)]
       (t/is (nil? (#'decoding/to-number "a" "abc"))))))

(t/deftest parse-chunk-metadata-with-separator-test
  (let [alphabet "abcd"]
    (t/is (= {:chunk          "bc"
              :next-remaining "d"
              :next-alphabet  (alphabet/consistent-shuffle alphabet)}
             (#'decoding/parse-chunk-metadata "bcad" alphabet)))))

(t/deftest parse-chunk-metadata-without-separator-test
  (let [alphabet "abcd"]
    (t/is (= {:chunk          "bcd"
              :next-remaining ""
              :next-alphabet  alphabet}
             (#'decoding/parse-chunk-metadata "bcd" alphabet)))))

#?(:clj
   (do
     (t/deftest decode-overflow-args-gen-branches-test
       (with-redefs [platform/decode-overflow-sqid-available? (constantly true)
                     platform/decode-overflow-sqid "overflow"
                     init/sqids (fn [_]
                                  (results/ok {:alphabet "abc"
                                               :min-length 0
                                               :block-list #{}}))]
         (t/is (= [{:alphabet "abc"
                    :min-length 0
                    :block-list #{}}
                   "overflow"]
                  (gen/generate (decoding-generators/decode-overflow-args)))))
       (with-redefs [platform/decode-overflow-sqid-available? (constantly true)
                     init/sqids (fn [_]
                                  (results/error ::error
                                                 ::stage
                                                 "failed"
                                                 {}))]
         (let [[sqids-config sqid] (gen/generate (decoding-generators/decode-overflow-args))]
           (t/is (s/valid? :org.sqids.clojure.init/sqids sqids-config))
           (t/is (= decoding-generators/invalid-character-sqid sqid))))
       (with-redefs [platform/decode-overflow-sqid-available? (constantly false)]
         (let [[_ sqid] (gen/generate (decoding-generators/decode-overflow-args))]
           (t/is (= decoding-generators/invalid-character-sqid sqid)))))

     (t/deftest decode-args-gen-includes-overflow-generator-when-available-test
       (with-redefs [platform/decode-overflow-sqid-available? (constantly true)]
         (t/is (some? (decoding-generators/decode-args)))))

     (t/deftest to-number-step-overflow-branch-test
       (with-redefs [platform/decode-step (fn [_ _ _] nil)]
         (t/is (nil? (#'decoding/to-number "a" "abc")))))

     (t/deftest decode-canonical-args-gen-encode-error-fallback-test
       (with-redefs [encoding/encode (fn [_ _]
                                       (results/error ::error
                                                      ::stage
                                                      "failed"
                                                      {}))]
         (let [[_ sqid] (gen/generate (decoding-generators/decode-canonical-args))]
           (t/is (= "" sqid)))))))
