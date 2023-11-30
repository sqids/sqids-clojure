(ns org.sqids.clojure
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as string]))

(defn ^:private conform!
  [spec input]
  (let [conformed (s/conform spec input)]
    (when (s/invalid? conformed)
      (throw (ex-info "Invalid input" (s/explain-data spec input))))
    conformed))

;; TODO: Replace with constants from sqids-java once
;; https://github.com/sqids/sqids-java/pull/7 is merged
(def min-alphabet-length 3)
(def min-length-limit 255)

(s/def ::alphabet-string
  string?)

(s/def ::alphabet-distinct
  #(apply distinct? %))

(s/def ::alphabet-min-length
  #(>= (count %) min-alphabet-length))

(s/def ::alphabet-no-multibyte
  #(= (count %) (count (.getBytes ^String %))))

(s/def ::alphabet
  (s/with-gen
    (s/and ::alphabet-string
           ::alphabet-distinct
           ::alphabet-min-length
           ::alphabet-no-multibyte)
    #(->> {:min-elements min-alphabet-length}
          ;; TODO: Once https://github.com/sqids/sqids-java/pull/10 is merged,
          ;; change to:
          ;;
          ;; (gen/set (gen/fmap char (gen/choose 0 127)))
          ;;
          ;; because regex chars are currently treated improperly
          (gen/set (gen/char-alphanumeric))
          (gen/fmap string/join))))

(s/def ::min-length (s/int-in 0 (inc min-length-limit)))

(s/def ::block-list (s/coll-of string? :kind set?))

(s/def ::options
  (s/keys :opt-un [::alphabet ::min-length ::block-list]))

(def default-options
  (conform! ::options
            {:alphabet   org.sqids.Sqids$Builder/DEFAULT_ALPHABET
             :min-length org.sqids.Sqids$Builder/DEFAULT_MIN_LENGTH
             :block-list (set org.sqids.Sqids$Builder/DEFAULT_BLOCK_LIST)}))

(defn sqids
  "Constructs an ISqids. Supported options in `options`:

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
              (conform! ::options)
              (merge default-options))

         instance
         (.. (org.sqids.Sqids/builder)
             (alphabet alphabet)
             (minLength min-length)
             (blockList block-list)
             build)]

     (->> instance
          (assoc complete-options :instance)
          (conform! ::sqids)))))

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
  [{:keys [instance]} nat-ints]
  (conform! ::sqid
            (.encode ^org.sqids.Sqids (conform! ::instance instance)
                     (mapv long (conform! ::nat-ints nat-ints)))))

(defn decode
  "Decodes a Sqid string into numbers. Arguments:

  | name   | description                                  |
  |--------|----------------------------------------------|
  | `s`    | A map returned by `org.sqids.clojure/sqids`. |
  | `sqid` | A Sqid string.                               |

  Returns an empty vector if `sqid` is empty. For particularly long Sqids,
  results may be negative due to long overflow; these results should be
  considered invalid."
  [{:keys [instance]} sqid]
  (conform! ::ints
            (vec (.decode ^org.sqids.Sqids (conform! ::instance instance)
                          (conform! ::sqid sqid)))))

(s/def ::instance
  #(instance? org.sqids.Sqids %))

(s/def ::sqids
  (s/with-gen
    (s/keys :req-un [::instance ::alphabet ::min-length ::block-list])
    #(gen/fmap sqids (s/gen ::options))))

(s/fdef sqids
        :args (s/alt :nullary (s/cat)
                     :unary   (s/cat :options ::options))
        :ret ::sqids)

(s/def ::nat-ints (s/coll-of nat-int? :kind sequential?))

(s/def ::ints (s/coll-of int? :kind vector?))

(s/def ::sqid
  (s/with-gen
    string?
    (fn []
      (->> (gen/tuple (s/gen ::sqids) (s/gen ::nat-ints))
           (gen/fmap
             (fn [[s nat-ints]]
               (try
                 (encode s nat-ints)
                 (catch RuntimeException _
                   ;; TODO: Catch a more specific exception from sqids-java once
                   ;; present.
                   nil))))
           (gen/such-that some?)))))

(s/fdef encode
        :args (s/cat :s ::sqids :nat-ints ::nat-ints)
        :ret  ::sqid
        :fn   (fn [info]
                (let [{:keys [ret args]}   info
                      {:keys [s nat-ints]} args]
                  (= nat-ints (decode s ret)))))

(s/fdef decode
        :args (s/cat :s ::sqids :sqid ::sqid)
        :ret  ::ints
        :fn   (fn [info]
                (let [{:keys [ret args]} info]
                  (if (some neg? ret)
                    ;; TODO: Remove this once
                    ;; https://github.com/sqids/sqids-java/issues/12 is resolved.
                    true
                    (let [{:keys [s sqid]}
                          args

                          expected
                          (if (set/subset? (set sqid) (-> s :alphabet set))
                            (->> ret
                                 (encode s)
                                 (decode s))
                            [])]

                      (= ret expected))))))
