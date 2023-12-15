(ns org.sqids.clojure.spec
  (:require
    [borkdude.dynaload :refer [dynaload] :include-macros true]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]
    [org.sqids.clojure :as-alias sqids]
    [org.sqids.clojure.alphabet :as-alias alphabet]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.decoding :as-alias decoding]
    [org.sqids.clojure.encoding :as-alias encoding]
    [org.sqids.clojure.init :as-alias init]
    [org.sqids.clojure.platform :as platform]))

(defn conform!
  [spec input]
  (let [conformed (s/conform spec input)]
    (when (s/invalid? conformed)
      (throw (ex-info (s/explain-str spec input) (s/explain-data spec input))))
    conformed))

(def min-length-limit
  255)

(def min-alphabet-length
  3)

(def max-char-code
  127)

(s/def ::init/block-list
  (s/coll-of string? :kind set?))

(s/def ::block-list/block-list
  (s/and ::init/block-list
         #(every? (fn [w] (= w (str/lower-case w))) %)
         #(every? (fn [w] (>= (count w) block-list/min-word-length)) %)))

(s/def ::alphabet/distinct
  #(apply distinct? %))

(s/def ::alphabet/min-length
  #(>= (count %) min-alphabet-length))

(s/def ::alphabet/no-multibyte
  #(every? (fn [c] (<= (platform/char-code c) max-char-code)) %))

(s/def ::alphabet/alphabet
  (s/with-gen
    (s/and string?
           ::alphabet/distinct
           ::alphabet/min-length
           ::alphabet/no-multibyte)
    #(->> {:min-elements min-alphabet-length}
          (gen/set (gen/fmap char (gen/choose 0 max-char-code)))
          (gen/fmap str/join))))

(s/def ::encoding/min-length
  (s/int-in 0 (inc min-length-limit)))

(s/def ::init/options
  (s/keys :opt-un [::alphabet/alphabet
                   ::encoding/min-length
                   ::init/block-list]))

(let [sqids (dynaload 'org.sqids.clojure/sqids)]
  (s/def ::init/sqids
    (s/with-gen
      (s/keys :req-un [::alphabet/alphabet
                       ::encoding/min-length
                       ::block-list/block-list])
      #(gen/fmap sqids (s/gen ::init/options)))))

(s/def ::decoding/sqid
  string?)

(s/def ::decoding/number
  ::platform/integer)

(s/def ::encoding/number
  (s/and ::decoding/number
         #(not (neg? %))
         #(<= % platform/max-value)))

(s/def ::decoding/numbers
  (s/coll-of ::decoding/number :kind vector?))

(s/def ::encoding/numbers
  (s/coll-of ::encoding/number :kind sequential?))

(s/def ::decoding/args
  (s/cat :sqids ::init/sqids :sqid ::decoding/sqid))

(s/def ::encoding/args
  (s/cat :sqids ::init/sqids :numbers ::encoding/numbers))
