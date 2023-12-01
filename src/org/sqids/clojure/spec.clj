(ns org.sqids.clojure.spec
  (:require
    [borkdude.dynaload :refer [dynaload]]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as string]))

(defn conform!
  [spec input]
  (let [conformed (s/conform spec input)]
    (when (s/invalid? conformed)
      (throw (ex-info "Invalid input" (s/explain-data spec input))))
    conformed))

;; TODO: Replace with constants from sqids-java once
;; https://github.com/sqids/sqids-java/pull/7 is merged
(def min-alphabet-length
  3)

(def min-length-limit
  255)

(defn ^:private sqids
  [& args]
  (apply (dynaload 'org.sqids.clojure/sqids) args))

(defn ^:private decode
  [& args]
  (apply (dynaload 'org.sqids.clojure/decode) args))

(defn ^:private encode
  [& args]
  (apply (dynaload 'org.sqids.clojure/encode) args))

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

(s/def ::min-length
  (s/int-in 0 (inc min-length-limit)))

(s/def ::block-list
  (s/coll-of string? :kind set?))

(s/def ::options
  (s/keys :opt-un [::alphabet ::min-length ::block-list]))

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

(s/def ::nat-ints
  (s/coll-of nat-int? :kind sequential?))

(s/def ::ints
  (s/coll-of int? :kind vector?))

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

(s/fdef org.sqids.clojure/encode
  :args (s/cat :s ::sqids :nat-ints ::nat-ints)
  :ret  ::sqid
  :fn   (fn [info]
          (let [{:keys [ret args]}   info
                {:keys [s nat-ints]} args]
            (= nat-ints (decode s ret)))))

(s/fdef org.sqids.clojure/decode
  :args (s/cat :s ::sqids :sqid ::sqid)
  :ret  ::ints
  :fn   (fn [info]
          (let [{:keys [ret args]} info]
            (if (some neg? ret)
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
