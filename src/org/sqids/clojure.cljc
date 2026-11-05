(ns org.sqids.clojure
  (:require
    #?@(:clj
        [[clojure.edn :as edn]
         [clojure.java.io :as io]])
    [clojure.string :as str])
  #?(:clj
     (:import
       (java.util
         Locale))
     :cljs
     (:import
       (goog.string
         StringBuffer)))
  #?(:cljs
     (:require-macros
       [org.sqids.clojure :as sqids-macros])))

#?(:clj (set! *warn-on-reflection* true))

#?(:clj
   (defmacro ^:private read-default-blocklist
     "Embeds the required bundled blocklist at compile time on both platforms."
     []
     (let [path "org/sqids/clojure/blocklist.json"
           resource (io/resource path)]
       (when-not resource
         (throw (ex-info "Missing bundled Sqids blocklist" {:resource path})))
       (set (edn/read-string (slurp resource))))))

(def default-options
  "Default, uncompiled Sqids options, suitable for passing to `sqids`."
  {:alphabet "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
   :min-length 0
   :block-list #?(:clj (read-default-blocklist)
                  :cljs (sqids-macros/read-default-blocklist))})

(deftype ^{:no-doc true :doc "Immutable, non-associative compiled options used by encode and decode."}
 Sqids
  [alphabet min-length block-list-index alphabet-set])

(defn- require-instance
  "Checks that the argument is a Sqids instance and returns it."
  [candidate]
  (if (instance? Sqids candidate)
    candidate
    (throw (ex-info "Create an instance with sqids before encoding or decoding" {:instance candidate}))))

(defn- rotate-left
  "Returns `alphabet` rotated left by `offset` characters."
  [alphabet offset]
  (str (subs alphabet offset) (subs alphabet 0 offset)))

(defn- consistent-shuffle
  "Applies the deterministic Sqids shuffle to an owned character array."
  [alphabet]
  (let [characters #?(:clj (.toCharArray ^String alphabet)
                      :cljs (.split alphabet ""))
        size (long (alength characters))]
    (loop [left 0
           right (dec size)]
      (if (pos? right)
        (let [left-char (aget characters left)
              right-char (aget characters right)
              swap-index (rem (+ (* left right)
                                 #?(:clj (int left-char) :cljs (.charCodeAt left-char 0))
                                 #?(:clj (int right-char) :cljs (.charCodeAt right-char 0)))
                              size)]
          (aset characters left (aget characters swap-index))
          (aset characters swap-index left-char)
          (recur (inc left) (dec right)))
        #?(:clj (String. characters)
           :cljs (.join characters ""))))))

(defn- lower-case
  "Normalizes matching independently of the JVM's default locale."
  [word]
  #?(:clj (.toLowerCase ^String word Locale/ROOT)
     :cljs (str/lower-case word)))

(defn- normalize-blocklist
  "Lowercases and deduplicates words, removing those too short or impossible for this alphabet."
  [words alphabet]
  (let [alphabet-set (set (lower-case alphabet))]
    (into #{}
          (comp (map lower-case)
                (filter #(>= (count %) 3))
                (filter #(every? alphabet-set %)))
          words)))

(defn- index-blocklist
  "Groups normalized words by matching rule; nil disables blocklist checks."
  [words]
  (when (seq words)
    (reduce (fn [index word]
              (update index
                      (cond
                        (= 3 (count word)) :exact
                        (re-find #"\d" word) :prefix-suffix
                        :else :contains)
                      conj word))
            {:exact #{} :prefix-suffix [] :contains []}
            words)))

(defn- matches-any?
  "Scans a word vector with early termination and no per-word sequence nodes."
  [pred words]
  (reduce (fn [_ word]
            (if (pred word)
              (reduced true)
              false))
          false
          words))

(defn- blocked-id?
  "Returns whether an ID matches a compiled blocklist index."
  [index id]
  (boolean
    (when (and index (>= (count id) 3))
      (let [id (lower-case id)]
        (or (contains? (:exact index) id)
            (and (> (count id) 3)
                 (or (matches-any? #(or (str/starts-with? id %) (str/ends-with? id %))
                                   (:prefix-suffix index))
                     (matches-any? #(str/includes? id %) (:contains index)))))))))

(defn- validated-options
  "Applies defaults and validates options before compiling an instance."
  [options]
  (when-not (map? options)
    (throw (ex-info "Sqids options must be a map" {:options options})))
  (let [{:keys [alphabet min-length block-list] :as options} (merge default-options options)]
    (when-not (and (string? alphabet)
                   (<= 3 (count alphabet) 128)
                   (= (count alphabet) (count (set alphabet)))
                   (not (re-find #"[^\u0000-\u007f]" alphabet)))
      (throw (ex-info "Alphabet must contain at least 3 unique ASCII characters" {:alphabet alphabet})))
    (when-not (and (integer? min-length) (<= 0 min-length 255))
      (throw (ex-info "Minimum length must be an integer between 0 and 255" {:min-length min-length})))
    (when-not (and (set? block-list) (every? string? block-list))
      (throw (ex-info "Blocklist must be a set of strings" {:block-list block-list})))
    options))

(defn sqids
  "Builds a reusable Sqids instance from an options map.

  Options are `:alphabet` (at least 3 unique ASCII characters), `:min-length`
  (integer from 0 to 255), and `:block-list` (a set of strings). An omitted
  blocklist uses the bundled default; an empty set disables filtering.

  The returned instance does not support associative operations. Retain the
  options separately and call `sqids` again to change them. Invalid options
  throw ex-info."
  ([]
   (sqids {}))
  ([options]
   (let [{:keys [alphabet min-length block-list]} (validated-options options)]
     (->Sqids (consistent-shuffle alphabet)
              (int min-length)
              (index-blocklist (normalize-blocklist block-list alphabet))
              (set alphabet)))))

(defn- to-id
  "Encodes one integer, reserving the first alphabet character for separators."
  [number ^String alphabet]
  (let [base (dec (count alphabet))
        out #?(:clj (StringBuilder.) :cljs (array))]
    (loop [number number]
      (let [digit (.charAt alphabet (inc (int (rem number base))))
            quotient (quot number base)]
        #?(:clj (.append ^StringBuilder out ^char digit)
           :cljs (.push out digit))
        (if (pos? quotient)
          (recur quotient)
          #?(:clj (.toString (.reverse ^StringBuilder out))
             :cljs (.join (.reverse out) "")))))))

(defn- prefix-offset
  "Computes the initial prefix offset once, before blocklist retries."
  [^String alphabet numbers]
  (let [size (count alphabet)]
    (reduce-kv (fn [offset index number]
                 (let [character (.charAt alphabet (int (rem number size)))]
                   (rem (+ offset index #?(:clj (int character)
                                           :cljs (.charCodeAt character 0)))
                        size)))
               (rem (count numbers) size)
               numbers)))

(defn- encode-unpadded
  "Builds the unpadded ID, returning it and the final alphabet."
  [numbers ^String alphabet offset]
  (let [rotated (rotate-left alphabet offset)
        out #?(:clj (StringBuilder.) :cljs (StringBuffer.))
        number-count (count numbers)]
    (.append ^StringBuilder out ^char (.charAt ^String rotated 0))
    (loop [current-alphabet (str/reverse rotated)
           index 0]
      (let [digits (to-id (nth numbers index) current-alphabet)]
        (.append ^StringBuilder out ^String digits)
        (if (< index (dec number-count))
          (do
            (.append ^StringBuilder out ^char (.charAt ^String current-alphabet 0))
            (recur (consistent-shuffle current-alphabet) (inc index)))
          [(.toString ^StringBuilder out)
           current-alphabet])))))

(defn- pad-to-min-length
  "Adds Sqids padding, bounded by the configured maximum of 255 characters."
  [id min-length alphabet]
  (if (<= min-length (count id))
    id
    (loop [id (str id (.charAt ^String alphabet 0))
           alphabet alphabet]
      (if (<= min-length (count id))
        id
        (let [alphabet (consistent-shuffle alphabet)
              required (- min-length (count id))]
          (recur (str id (subs alphabet 0 (min required (count alphabet))))
                 alphabet))))))

(defn- validated-numbers
  "Realizes and validates the input once so encoding and retries can index it."
  [numbers]
  (when-not (sequential? numbers)
    (throw (ex-info "Numbers must be a sequence of non-negative integers" {:numbers numbers})))
  (let [numbers (vec numbers)]
    (doseq [number numbers]
      (when-not #?(:clj (and (integer? number) (not (neg? number)))
                   :cljs (and (js/Number.isSafeInteger number) (<= 0 number)))
        (throw (ex-info "Numbers must be non-negative integers within the platform range" {:number number}))))
    numbers))

(defn- encode-with-retries
  "Tries successive prefix offsets until the padded ID passes the blocklist."
  [^Sqids instance numbers]
  (let [alphabet (.-alphabet instance)
        min-length (.-min-length instance)
        block-list-index (.-block-list-index instance)
        offset (prefix-offset alphabet numbers)
        size (count alphabet)]
    (loop [attempt 0]
      (when (> attempt size)
        (throw (ex-info "Reached max attempts to re-generate the ID"
                        {:numbers numbers :attempts attempt})))
      (let [[id final-alphabet] (encode-unpadded numbers alphabet (rem (+ offset attempt) size))
            id (pad-to-min-length id min-length final-alphabet)]
        (if (blocked-id? block-list-index id)
          (recur (inc attempt))
          id)))))

(defn encode
  "Encodes a sequence of non-negative integers using an instance from `sqids`.

  JVM integers may be arbitrarily large; ClojureScript numbers must be safe
  integers (at most 9007199254740991). Invalid arguments or exhaustion of all
  blocklist retries throw ex-info. An empty sequence encodes to an empty string."
  [sqids-instance numbers]
  (let [instance (require-instance sqids-instance)
        numbers (validated-numbers numbers)]
    (if (empty? numbers)
      ""
      (encode-with-retries instance numbers))))

(defn- valid-characters?
  "Checks the entire ID, including padding, against cached alphabet membership."
  [alphabet-set ^String id]
  (let [length (count id)]
    (loop [index 0]
      (or (= index length)
          (and (contains? alphabet-set (.charAt id index))
               (recur (inc index)))))))

(defn- to-number
  "Decodes a character range without allocating a substring."
  [^String id ^String alphabet start end]
  (let [base (dec (count alphabet))]
    ;; A boxed initial value permits JVM promotion to BigInt on large chunks.
    (loop [number (identity 0)
           index start]
      (if (= index end)
        number
        (let [digit (dec (.indexOf alphabet #?(:clj (int (.charAt id index))
                                               :cljs (.charAt id index))))
              number #?(:clj (+' (*' number base) digit)
                        :cljs (+ (* number base) digit))]
          #?(:clj (recur number (inc index))
             :cljs (when (js/Number.isSafeInteger number)
                     (recur number (inc index)))))))))

(defn- decode-numbers
  "Reads the digit chunks after the prefix; an empty chunk marks the start of padding."
  [^String sqid ^String alphabet]
  (let [size (count sqid)
        offset (.indexOf alphabet #?(:clj (int (.charAt sqid 0))
                                     :cljs (.charAt sqid 0)))]
    (loop [cursor 1
           alphabet (str/reverse (rotate-left alphabet offset))
           numbers (transient [])]
      (let [separator (.charAt ^String alphabet 0)
            separator-index (.indexOf sqid #?(:clj (int separator) :cljs separator) (int cursor))
            end (if (neg? separator-index) size separator-index)]
        (if (= cursor end)
          (persistent! numbers)
          (if-some [number (to-number sqid alphabet cursor end)]
            (let [numbers (conj! numbers number)
                  next-cursor (inc end)]
              (if (< next-cursor size)
                (recur (long next-cursor) (consistent-shuffle alphabet) numbers)
                (persistent! numbers)))
            []))))))

(defn decode
  "Decodes a string using an instance from `sqids`, returning a vector.

  Empty or malformed IDs return `[]`. ClojureScript also returns `[]` if any
  decoded integer exceeds 9007199254740991. Non-string inputs throw ex-info."
  [sqids-instance ^String sqid]
  (let [^Sqids instance (require-instance sqids-instance)]
    (when-not (string? sqid)
      (throw (ex-info "Sqid must be a string" {:sqid sqid})))
    (if (or (empty? sqid) (not (valid-characters? (.-alphabet-set instance) sqid)))
      []
      (decode-numbers sqid (.-alphabet instance)))))
