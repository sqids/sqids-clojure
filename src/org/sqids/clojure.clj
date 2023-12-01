(ns org.sqids.clojure
  (:require
    [org.sqids.clojure.spec :as spec]))

(def default-options
  (spec/conform! ::spec/options
                 {:alphabet   org.sqids.Sqids$Builder/DEFAULT_ALPHABET
                  :min-length org.sqids.Sqids$Builder/DEFAULT_MIN_LENGTH
                  :block-list (set org.sqids.Sqids$Builder/DEFAULT_BLOCK_LIST)}))

(defn sqids
  "Builds a sqids map. Supported options in `options`:

  | key           | description                                                                                                                                                                                                                                                                                                                               |
  |---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
  | `:alphabet`   | A string. The alphabet to which a vector of numbers will be encoded to a Sqid and from which a Sqid will be decoded to a vector of numbers. Minimum length is `org.sqids.Sqids$MIN_ALPHABET_LENGTH`. Must not contain multi-byte characters. Must consistent of unique characters. Default is `org.sqids.Sqids$Builder/DEFAULT_ALPHABET`. |
  | `:min-length` | An int. The minimum length of an Sqid. Must be in [0, `org.sqids.Sqids$MIN_LENGTH_LIMIT`]. Default is `org.sqids.Sqids$Builder/DEFAULT_MIN_LENGTH`.                                                                                                                                                                                       |
  | `:block-list` | A set of strings. Any words that should be excluded from an encoded Sqid. Any words shorter than `org.sqids.Sqids/MIN_BLOCK_LIST_WORD_LENGTH` will be excluded.                                                                                                                                                                           | "
  ([]
   (sqids {}))
  ([options]
   (let [{:keys [alphabet min-length block-list] :as complete-options}
         (->> options
              (spec/conform! ::spec/options)
              (merge default-options))

         instance
         (.. (org.sqids.Sqids/builder)
             (alphabet alphabet)
             (minLength min-length)
             (blockList block-list)
             build)]

     (->> instance
          (assoc complete-options :instance)
          (spec/conform! ::spec/sqids)))))

;; NOTE: Generative testing is disabled for encode because it may throw a
;; RuntimeException.
(defn ^:no-gen encode
  "Encodes numbers into a Sqid string. Arguments:

  | name       | description                                                                   |
  |------------|-------------------------------------------------------------------------------|
  | `s`        | A map returned by `org.sqids.clojure/sqids`.                                  |
  | `nat-ints` | A sequential collection of natural integers that will be encoded into a Sqid. |

  Returns an empty string if `nat-ints` is empty. Throws a `RuntimeException` if
  any value in `nat-ints` is negative. May throw a `RuntimeException` if a Sqid
  cannot be generated due to too many attempts."
  [s nat-ints]
  (let [^org.sqids.Sqids instance
        (:instance (spec/conform! ::spec/sqids s))

        numbers
        (mapv long (spec/conform! ::spec/nat-ints nat-ints))

        result
        (.encode instance numbers)]

    (spec/conform! ::spec/sqid result)))

(defn decode
  "Decodes a Sqid string into numbers. Arguments:

  | name   | description                                  |
  |--------|----------------------------------------------|
  | `s`    | A map returned by `org.sqids.clojure/sqids`. |
  | `sqid` | A Sqid string.                               |

  Returns an empty vector if `sqid` is empty. For particularly long Sqids,
  results may be negative due to long overflow; these results should be
  considered invalid."
  [s sqid]
  (let [^org.sqids.Sqids instance
        (:instance (spec/conform! ::spec/sqids s))

        id
        (spec/conform! ::spec/sqid sqid)

        numbers
        (vec (.decode instance id))]

    (spec/conform! ::spec/ints numbers)))
