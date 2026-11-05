(ns org.sqids.clojure.parity
  (:require
    [clojure.string :as str]
    [clojure.test.check.generators :as gen]
    [org.sqids.clojure :as sut]))

(def js-safe-int-max
  "Largest non-negative integer shared with the JavaScript reference."
  9007199254740991)

(defn fixed-requests
  "Returns curated success and rejection cases from the shared Sqids contract."
  []
  [{:id "default-simple"
    :op "roundtrip"
    :options {}
    :numbers [1 2 3]}
   {:id "default-blocklist"
    :op "roundtrip"
    :options {}
    :numbers [4572721]}
   {:id "custom-alphabet"
    :op "roundtrip"
    :options {:alphabet "0123456789abcdef"}
    :numbers [1 2 3]}
   {:id "custom-min-length"
    :op "roundtrip"
    :options {:min-length 10}
    :numbers [1 2 3]}
   {:id "custom-block-list"
    :op "roundtrip"
    :options {:block-list ["ArUO"]}
    :numbers [100000]}
   {:id "invalid-character"
    :op "decode"
    :options {}
    :sqid "*"}
   {:id "repeated-blocklist-retries"
    :op "roundtrip"
    :options {:block-list ["JSwXFaosAN" "OCjV9JK64o" "rBHf" "79SM" "7tE6"]}
    :numbers [1000000 2000000]}
   {:id "blocklist-uppercase-alphabet"
    :op "roundtrip"
    :options {:alphabet "ABCDEFGHIJKLMNOPQRSTUVWXYZ" :block-list ["sxnzkl"]}
    :numbers [1 2 3]}
   {:id "blocklist-retry-exhaustion"
    :op "encode"
    :options {:alphabet "abc" :min-length 3 :block-list ["cab" "abc" "bca"]}
    :numbers [0]
    :expect-error? true}
   {:id "negative-number"
    :op "encode"
    :options {}
    :numbers [-1]
    :expect-error? true}])

(defn boundary-requests
  "Exercises safe-integer edges across small alphabets and padding boundaries."
  []
  (vec
    (for [[alphabet-index alphabet-option]
          (map-indexed vector [(:alphabet sut/default-options)
                               "abc" "0123456789abcdef" "\u0000\t .[]\\"
                               (apply str (map char (range 128)))])
          minimum-length [0 1 62 255]
          [numbers-index numbers]
          (map-indexed vector [[] [0] [js-safe-int-max]
                               [0 1 255 256 4294967295 4294967296
                                9007199254740990 js-safe-int-max]])]
      {:id (format "boundary-%d-%d-%d" alphabet-index minimum-length numbers-index)
       :op "roundtrip"
       :options {:alphabet alphabet-option :min-length minimum-length :block-list []}
       :numbers numbers})))

(defn noncanonical-requests
  "Checks alternate prefixes, truncation, trailing padding and blocked ID decoding."
  []
  (mapv (fn [index sqid]
          {:id (format "noncanonical-%02d" index)
           :op "decode"
           :options {:block-list ["86Rf07" "se8ojk" "ARsz1p" "Q8AI49" "5sQRZO"]}
           :sqid sqid})
        (range)
        ["" "8" "86" "86Rf" "86Rf07" "se8ojk" "ARsz1p" "Q8AI49" "5sQRZO"
         "86Rf07xd4z" "86Rf07xd4zBmiJXQG6rw1fC9tZ0sbeA2nvIhU3Oq5V7"]))

(defn invalid-options-requests
  "Requires both implementations to reject invalid shared constructor options."
  []
  (mapv (fn [index options]
          {:id (format "invalid-options-%02d" index)
           :op "sqids"
           :options options
           :expect-error? true})
        (range)
        [{:alphabet ""} {:alphabet "ab"} {:alphabet "aab"} {:alphabet "abcé"}
         {:min-length -1} {:min-length 256}]))

(def ^:private options-generator
  "Generates ASCII alphabets, padding and alphabet-compatible blocklist words."
  (gen/bind
    (gen/one-of [(gen/return (:alphabet sut/default-options))
                 (gen/fmap #(apply str %) (gen/vector-distinct
                                            (gen/elements (mapv char (range 128)))
                                            {:min-elements 3 :max-elements 128}))])
    (fn [alphabet]
      (gen/fmap
        (fn [[minimum words]]
          {:alphabet alphabet :min-length minimum :block-list words})
        (gen/tuple (gen/choose 0 255)
                   (gen/vector
                     (gen/fmap #(apply str %)
                               (gen/vector (gen/elements (vec (str/lower-case alphabet))) 0 12))
                     0 8))))))

(def ^:private numbers-generator
  "Exercises arbitrary safe integers with extra weight on arithmetic boundaries."
  (gen/vector
    (gen/frequency
      [[3 (gen/elements [0 1 2 255 256 65535 65536 2147483647 2147483648
                         4294967295 4294967296 9007199254740990 js-safe-int-max])]
       [7 (gen/large-integer* {:min 0 :max js-safe-int-max})]])
    0 8))

(defn- samples
  "Produces a reproducible sequence using a separate seed for each case."
  [generator n seed]
  (mapv #(gen/generate generator 12 (+ seed %)) (range n)))

(defn requests
  "Builds the single corpus evaluated by JVM, compiled CLJS and sqids-spec."
  []
  (into [] cat
        [(fixed-requests)
         (boundary-requests)
         (noncanonical-requests)
         (invalid-options-requests)
         (map-indexed
           (fn [index [options numbers]]
             {:id (str "generated-roundtrip-" index) :op "roundtrip"
              :options options :numbers numbers})
           (samples (gen/tuple options-generator numbers-generator) 384 42000))
         (map-indexed
           (fn [index [options sqid]]
             {:id (str "generated-decode-" index) :op "decode" :options options :sqid sqid})
           (samples
             (gen/bind options-generator
                       (fn [options]
                         ;; Seven digits in the largest alphabet remain JS-safe.
                         (gen/fmap #(vector options (apply str %))
                                   (gen/vector (gen/elements (vec (:alphabet options))) 0 8))))
             256 44000))
         (map-indexed
           (fn [index options]
             {:id (str "invalid-character-" index) :op "decode"
              :options options :sqid (str (subs (:alphabet options) 0 (mod index 3)) "\u0100")})
           (samples options-generator 32 43000))]))
