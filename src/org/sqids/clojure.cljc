(ns org.sqids.clojure
  (:require
    [clojure.spec.alpha :as s]
    [org.sqids.clojure.decoding :as decoding]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.spec :as spec]))

(def default-options
  (spec/conform! ::init/sqids init/default-options))

(defn sqids
  "Builds a sqids map. Supported options in `options`:

  | key           | description                                                                                                                                                                                                                                                                                                                                             |
  |---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
  | `:alphabet`   | A string. The alphabet to which a vector of numbers will be encoded to a Sqid and from which a Sqid will be decoded to a vector of numbers. Minimum length is `org.sqids.clojure.alphabet/min-alphabet-length`. Must not contain multi-byte characters. Must consistent of unique characters. Default is `org.sqids.clojure.alphabet/default-alphabet`. |
  | `:min-length` | An int. The minimum length of an Sqid. Must be in [0, `org.sqids.clojure.encode/min-length-limit`]. Default is `org.sqids.clojure.encode/default-min-length`.                                                                                                                                                                                           |
  | `:block-list` | A set of strings. Any words that should be excluded from an encoded Sqid. Any words shorter than `org.sqids.clojure.block-list/min-block-list-word-length` will be excluded from the block list.                                                                                                                                                        |"
  ([]
   (sqids {}))
  ([options]
   (->> options
        (spec/conform! ::init/options)
        init/sqids
        (spec/conform! ::init/sqids))))

(defn encode
  "Encodes numbers into a Sqid string. Arguments:

  | name      | description                                                               |
  |-----------|---------------------------------------------------------------------------|
  | `sqids`   | A map returned by `org.sqids.clojure/sqids`.                              |
  | `numbers` | A sequential collection of natural ints that will be encoded into a Sqid. |"
  [sqids numbers]
  (->> [sqids numbers]
       (spec/conform! ::encoding/args)
       encoding/encode
       (spec/conform! ::decoding/sqid)))

(defn decode
  "Decodes a Sqid string into numbers. Arguments:

  | name    | description                                  |
  |---------|----------------------------------------------|
  | `sqids` | A map returned by `org.sqids.clojure/sqids`. |
  | `sqid`  | A Sqid string.                               |

  Returns an empty vector if `sqid` is empty. For particularly long Sqids,
  results may be negative due to long overflow; these results should be
  considered invalid."
  [sqids sqid]
  (->> [sqids sqid]
       (spec/conform! ::decoding/args)
       decoding/decode
       (spec/conform! ::decoding/numbers)))

(s/fdef sqids
  :args (s/alt :nullary (s/cat)
               :unary   (s/cat :options ::init/options))
  :ret ::init/sqids)

(s/fdef encode
  :args ::encoding/args
  :ret  ::decoding/sqid
  :fn   (fn [info]
          (let [{:keys [args ret]} info
                {:keys [sqids]}    args
                numbers            (decoding/decode {:sqids sqids :sqid ret})]
            (or (not (s/valid? ::encoding/numbers numbers))
                (try
                  (let [sqid (encoding/encode {:sqids sqids :numbers numbers})]
                    (= ret sqid))
                  (catch #?(:clj Exception :cljs :default) _
                    false))))))

(s/fdef decode
  :args ::decoding/args
  :ret  ::decoding/numbers
  :fn   (fn [info]
          (let [{:keys [args ret]} info
                {:keys [sqids]}    args]
            (or (not (s/valid? ::encoding/numbers ret))
                (try
                  (let [sqid    (encoding/encode {:sqids sqids :numbers ret})
                        numbers (decoding/decode {:sqids sqids :sqid sqid})]
                    (= ret numbers))
                  (catch #?(:clj Exception :cljs :default) _
                    false))))))
