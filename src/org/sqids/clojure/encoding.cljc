(ns org.sqids.clojure.encoding
  (:require
    [clojure.spec.alpha :as s]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.generators.encoding :as encoding-generators]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.platform :as platform]
    [org.sqids.clojure.results :as results]))

(defn- to-id
  "Encodes a non-negative integer into base-`alphabet` characters."
  [number alphabet]
  (let [base (count alphabet)]
    (loop [n   number
           out ()]
      (let [idx (int (mod n base))
            ;; `conj` on a list prepends, which builds base digits from least to
            ;; most significant and yields the final order once joined.
            out (conj out (nth alphabet idx))
            n   (quot n base)]
        (if (pos? n)
          (recur n out)
          (apply str out))))))

(defn- prefix-offset
  "Computes the deterministic prefix offset for an encode attempt."
  [alphabet numbers]
  (let [alpha-len (count alphabet)]
    ;; Spec-driven offset based on number positions and the selected alphabet
    ;; character codes, before retry attempt adjustment.
    (mod (reduce-kv (fn [acc index number]
                      (let [char-index (int (mod number alpha-len))]
                        (+ acc index (platform/char-code (nth alphabet char-index)))))
                    (count numbers)
                    numbers)
         alpha-len)))

(defn- encode-prefix
  "Encodes numbers with prefix/separators and returns `[id alphabet]`."
  [sqids numbers attempt]
  (let [base-alphabet    (:alphabet sqids)
        alpha-len        (count base-alphabet)
        adjusted-offset  (int (mod (+ (prefix-offset base-alphabet numbers)
                                      attempt)
                                   alpha-len))
        rotated-alphabet (alphabet/rotate-left base-alphabet adjusted-offset)
        prefix           (first rotated-alphabet)
        initial-alphabet (alphabet/reverse-str rotated-alphabet)
        number-count     (count numbers)]
    (loop [current-id       (str prefix)
           current-alphabet initial-alphabet
           number-index     0]
      (let [digit-alphabet (subs current-alphabet 1)
            updated-id     (str current-id
                                (to-id (nth numbers number-index)
                                       digit-alphabet))]
        (if (< number-index (dec number-count))
          (recur (str updated-id (first current-alphabet))
                 (alphabet/consistent-shuffle current-alphabet)
                 (inc number-index))
          [updated-id current-alphabet])))))

(defn- pad-to-min-length
  "Pads `id` to `min-length` via deterministic alphabet shuffles."
  [id min-length alphabet]
  (if (<= min-length (count id))
    id
    (loop [current-id       (str id (first alphabet))
           current-alphabet alphabet]
      (if (<= min-length (count current-id))
        current-id
        (let [shuffled-alphabet (alphabet/consistent-shuffle current-alphabet)
              required-length  (- min-length (count current-id))
              padded-id        (str current-id
                                    (subs shuffled-alphabet
                                          0
                                          (min required-length (count shuffled-alphabet))))]
          (recur padded-id shuffled-alphabet))))))

(defn- encode-numbers
  "Encodes numbers and retries with incremented attempts when blocked."
  [sqids numbers attempt]
  (if (> attempt (count (:alphabet sqids)))
    (results/encode-max-attempts ::encode-run-stage
                                 {:increment attempt
                                  :numbers   numbers})
    (let [[encoded-id current-alphabet] (encode-prefix sqids numbers attempt)
          padded-id                     (pad-to-min-length encoded-id
                                                           (:min-length sqids)
                                                           current-alphabet)]
      (if (block-list/blocked-id? (::block-list/index sqids) padded-id)
        (recur sqids numbers (inc attempt))
        (results/ok padded-id)))))

(defn ^:private encode*
  "Encodes numbers using initialized Sqids state and returns a result envelope."
  [{:keys [sqids numbers]}]
  (if (empty? numbers)
    (results/ok "")
    (encode-numbers sqids (vec numbers) 0)))

(defn encode
  "Encodes numbers into a Sqid and returns a non-throwing result envelope."
  [sqids-config numbers]
  (-> (results/attempt ::encode-init-stage
                       #(init/ensure-initialized sqids-config))
      (results/bind ::encode-args-stage
                    (fn [initialized-config]
                      (results/conform ::encode-args
                                       [initialized-config numbers]
                                       ::encode-args-stage)))
      (results/bind ::encode-run-stage
                    encode*)
      (results/bind ::encode-ret-stage
                    (fn [sqid-value]
                      (results/conform ::sqid
                                       sqid-value
                                       ::encode-ret-stage)))))

(s/def ::nat-int
  (s/with-gen
    (s/and integer? #(not (neg? %)) platform/in-range?)
    encoding-generators/nat-int))

(s/def ::nat-ints
  (s/coll-of ::nat-int :kind sequential?))

(s/def ::sqid
  string?)

(s/def ::encode-args
  (s/cat :sqids ::init/sqids
         :numbers ::nat-ints))

(s/def ::encode-result
  (s/or :ok (results/ok-result-for ::sqid)
        :error ::results/error-result))

(s/def ::sqids-input
  (s/with-gen
    any?
    #(encoding-generators/sqids-input init/min-length-limit)))

(s/def ::numbers-input
  (s/with-gen
    any?
    encoding-generators/numbers-input))
