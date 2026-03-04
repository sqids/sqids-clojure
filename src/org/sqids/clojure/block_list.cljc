(ns org.sqids.clojure.block-list
  (:require
    #?@(:clj
        [[clojure.edn :as edn]
         [clojure.java.io :as io]])
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.generators.block-list :as block-list-generators])
  #?(:cljs
     (:require-macros
       [org.sqids.clojure.block-list :as block-list-macros])))

(def min-word-length
  "Minimum blocklist word length considered for matching."
  3)

(def block-list-word-characters
  "Characters used for generated block-list words."
  (vec "abcdefghijklmnopqrstuvwxyz0123456789"))

#?(:clj
   (defmacro read-default
     "Loads and returns the bundled default blocklist words at macro expansion time."
     []
     (let [resource (io/resource "org/sqids/clojure/blocklist.json")]
       (if-not resource
         #{}
         (-> resource
             slurp
             edn/read-string
             set)))))

(def default-words
  "Default Sqids blocklist words loaded from the bundled resource."
  #?(:clj
     (read-default)
     :cljs
     (block-list-macros/read-default)))

(defn remove-invalid-words
  "Normalizes and filters blocklist words to lowercase alphabet-compatible entries."
  [block-list alphabet]
  (let [alphabet-set (set (str/lower-case alphabet))]
    ;; Normalize blocklist words into the same lowercase alphabet domain used
    ;; by encoding/decoding, and drop words that cannot ever match.
    (into #{}
          (comp (map str/lower-case)
                (filter #(>= (count %) min-word-length))
                (filter #(set/subset? (set %) alphabet-set)))
          block-list)))

(defn- index-bucket
  "Classifies a blocklist word by Sqids matching behavior."
  [word]
  (cond
    (<= (count word) min-word-length)
    :exact

    ;; Words that contain digits only match at boundaries per Sqids rules.
    (re-find #"\d" word)
    :prefix-suffix

    :else
    :contains))

(defn build-index
  "Builds blocklist match buckets for efficient blocked-id checks."
  [block-list]
  (reduce
    (fn [idx word]
      (update idx (index-bucket word) conj word))
    {:exact #{}
     :prefix-suffix #{}
     :contains #{}}
    block-list))

(defn blocked-id?
  "Returns true when `id` matches any indexed blocklist word."
  [idx id]
  (let [normalized-id (str/lower-case id)
        id-length     (count normalized-id)]
    (cond
      (< id-length min-word-length)
      false

      :else
      (or (contains? (:exact idx) normalized-id)
          (when (> id-length min-word-length)
            (or (some #(or (str/starts-with? normalized-id %)
                           (str/ends-with? normalized-id %))
                      (:prefix-suffix idx))
                (some #(str/includes? normalized-id %)
                      (:contains idx))))))))

(s/def ::block-list
  (s/with-gen
    (s/coll-of string? :kind set?)
    #(block-list-generators/block-list
       {:alphabet-option alphabet/default
        :word-characters block-list-word-characters
        :min-word-length min-word-length})))
