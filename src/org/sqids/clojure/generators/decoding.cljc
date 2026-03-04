(ns org.sqids.clojure.generators.decoding
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.generators.alphabet :as alphabet-generators]
    [org.sqids.clojure.generators.init :as init-generators]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.platform :as platform]
    [org.sqids.clojure.results :as results]))

(def init-sqids-spec-key
  "Spec key used for generated initialized Sqids configs."
  :org.sqids.clojure.init/sqids)

(def encoding-nat-ints-spec-key
  "Spec key used for generated natural integer vectors in decode roundtrips."
  :org.sqids.clojure.encoding/nat-ints)

(def invalid-character-sqid
  "Sqid character guaranteed to be outside the supported alphabet domain."
  "\u0100")

(defn sqid
  "Builds Sqid-like strings for decode argument coverage."
  []
  (gen/frequency
    [[2 (gen/return "")]
     [4 (gen/string-alphanumeric)]
     [4 (gen/fmap (partial apply str)
                  (gen/vector
                    (gen/elements alphabet-generators/printable-characters)
                    0
                    24))]]))

(defn decode-canonical-args
  "Builds canonical decode args by round-tripping generated numbers through encode."
  []
  (gen/bind (s/gen init-sqids-spec-key)
            (fn [sqids-config]
              (gen/fmap (fn [numbers]
                          (let [unblocked-config (assoc sqids-config
                                                        :block-list #{}
                                                        ::block-list/index (block-list/build-index #{}))
                                encode-result    (encoding/encode unblocked-config numbers)
                                sqid-value       (if (= ::results/ok (::results/status encode-result))
                                                   (::results/value encode-result)
                                                   "")]
                            [unblocked-config sqid-value]))
                        (s/gen encoding-nat-ints-spec-key)))))

(defn decode-invalid-char-args
  "Builds decode args whose Sqid includes characters outside the alphabet."
  []
  (gen/fmap (fn [sqids-config]
              [sqids-config invalid-character-sqid])
            (s/gen init-sqids-spec-key)))

(defn decode-overflow-args
  "Builds known-overflow decode args for CLJS safe-integer behavior checks."
  []
  (if (platform/decode-overflow-sqid-available?)
    (let [default-sqids-result (init/sqids {})]
      (if (= ::results/ok (::results/status default-sqids-result))
        (gen/return [(::results/value default-sqids-result)
                     platform/decode-overflow-sqid])
        (decode-invalid-char-args)))
    (decode-invalid-char-args)))

(defn decode-args
  "Builds decode argument tuples with canonical, invalid-char, and overflow coverage."
  []
  (let [core-generators [[8 (decode-canonical-args)]
                         [2 (decode-invalid-char-args)]]
        generators      (if (platform/decode-overflow-sqid-available?)
                          (conj core-generators [1 (decode-overflow-args)])
                          core-generators)]
    (gen/frequency generators)))

(defn decode-invalid-input-args
  "Builds decode arg tuples with invalid config or Sqid input types."
  []
  (let [invalid-sqid-input (gen/one-of
                             [(gen/return nil)
                              (gen/large-integer* {})
                              (gen/keyword)
                              (gen/vector (gen/large-integer* {}))
                              (gen/map (gen/keyword) (gen/large-integer* {}))])
        invalid-sqids-input (init-generators/invalid-sqids-input init/min-length-limit)]
    (gen/one-of
      [(gen/tuple invalid-sqids-input
                  invalid-sqid-input)
       (gen/tuple (s/gen init-sqids-spec-key)
                  invalid-sqid-input)
       (gen/tuple invalid-sqids-input
                  (sqid))])))

(defn decode-call-args
  "Builds decode call args that mix canonical cases with invalid input coverage."
  []
  (gen/frequency
    [[8 (decode-args)]
     [2 (decode-invalid-input-args)]]))
