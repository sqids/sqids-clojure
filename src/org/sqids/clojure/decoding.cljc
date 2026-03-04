(ns org.sqids.clojure.decoding
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.generators.decoding :as decoding-generators]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.platform :as platform]
    [org.sqids.clojure.results :as results]))

(defn- to-number
  "Decodes an ID chunk into a number for the provided alphabet."
  [id alphabet]
  (let [base (count alphabet)]
    (loop [acc             0
           remaining-chars (seq id)]
      (if (empty? remaining-chars)
        acc
        (when-some [idx (str/index-of alphabet (first remaining-chars))]
          (when-some [next-value (platform/decode-step acc base idx)]
            (recur next-value (next remaining-chars))))))))

(defn- parse-chunk-metadata
  "Splits remaining input into the next chunk and decode state."
  [remaining alphabet]
  (let [separator       (first alphabet)
        separator-index (str/index-of remaining separator)
        chunk-end       (or separator-index (count remaining))]
    ;; The first alphabet character is the per-round separator; the next round
    ;; uses a deterministic shuffle only when another separator exists.
    {:chunk          (subs remaining 0 chunk-end)
     :next-remaining (if separator-index
                       (subs remaining (inc chunk-end))
                       "")
     :next-alphabet  (if separator-index
                       (alphabet/consistent-shuffle alphabet)
                       alphabet)}))

(defn ^:private decode-raw
  "Decodes a Sqid string into a vector of numbers for initialized inputs."
  [{:keys [sqids sqid]}]
  (let [source-alphabet (:alphabet sqids)]
    (cond
      (empty? sqid)
      []

      (not (set/subset? (set sqid) (set source-alphabet)))
      []

      :else
      (let [prefix-char      (first sqid)
            prefix-offset    (str/index-of source-alphabet prefix-char)
            initial-alphabet (-> source-alphabet
                                 (alphabet/rotate-left prefix-offset)
                                 alphabet/reverse-str)]
        (loop [remaining        (subs sqid 1)
               current-alphabet initial-alphabet
               numbers          []]
          (if (empty? remaining)
            numbers
            (let [{decoded-chunk :chunk
                   next-remaining :next-remaining
                   next-alphabet :next-alphabet}
                  (parse-chunk-metadata remaining current-alphabet)]
              (if (empty? decoded-chunk)
                numbers
                (if-some [decoded-number (to-number decoded-chunk (subs current-alphabet 1))]
                  (recur next-remaining next-alphabet (conj numbers decoded-number))
                  [])))))))))

(defn decode
  "Decodes a Sqid and returns a non-throwing result envelope."
  [sqids-config sqid]
  (-> (results/attempt ::decode-init-stage
                       #(init/ensure-initialized sqids-config))
      (results/bind ::decode-args-stage
                    (fn [initialized-config]
                      (results/conform ::decode-args
                                       [initialized-config sqid]
                                       ::decode-args-stage)))
      (results/bind ::decode-run-stage
                    (fn [conformed-args]
                      (results/ok (decode-raw conformed-args))))
      (results/bind ::decode-ret-stage
                    (fn [decoded-values]
                      (results/conform ::decoded-ints
                                       decoded-values
                                       ::decode-ret-stage)))))

(s/def ::sqid
  (s/with-gen
    string?
    decoding-generators/sqid))

(s/def ::decoded-ints
  (s/coll-of ::encoding/nat-int :kind vector?))

(s/def ::decode-args
  (s/with-gen
    (s/cat :sqids ::init/sqids
           :sqid ::sqid)
    decoding-generators/decode-args))

(s/def ::decode-result
  (s/or :ok (results/ok-result-for ::decoded-ints)
        :error ::results/error-result))

(s/def ::decode-call-args
  (s/with-gen
    (s/cat :sqids-config any?
           :sqid any?)
    decoding-generators/decode-call-args))
