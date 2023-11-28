(ns org.sqids.clojure
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as string]))

(declare encode)
(declare decode)
(declare sqids)

(defprotocol ISqids

  (encode* [this numbers])

  (decode* [this id]))

(defn ^:private conform!
  [spec input]
  (let [conformed (s/conform spec input)]
    (when (s/invalid? conformed)
      (throw (ex-info "Invalid input" (s/explain-data spec input))))
    conformed))

(defrecord Sqids
  [instance alphabet min-length block-list]

  ISqids

  (encode*
    [_ numbers]
    (conform! ::id (.encode (conform! ::instance instance)
                            (conform! ::numbers (vec numbers)))))


  (decode*
    [_ id]
    (conform! ::numbers (vec (.decode (conform! ::instance instance)
                                      (conform! ::id id))))))

;; TODO: Replace with constants from sqids-java once
;; https://github.com/sqids/sqids-java/pull/7 is merged
(def min-alphabet-length 3)
(def min-length-limit 255)

(s/def ::alphabet
  (s/with-gen
   (s/and string?
          #(>= (count %) min-alphabet-length)
          #(= (count %) (count (.getBytes %)))
          #(= (count %) (count (set %))))
   #(gen/fmap string/join
              (gen/set (gen/char-alphanumeric)
                       {:min-elements min-alphabet-length}))))

(s/def ::min-length (s/int-in 0 (inc min-length-limit)))

(s/def ::block-list (s/coll-of string? :kind set?))

(s/def ::options
  (s/keys :opt-un [::alphabet ::min-length ::block-list]))

(declare encode)

(s/def ::instance
  #(instance? org.sqids.Sqids %))

(s/def ::record
  (s/with-gen
   (s/and #(instance? Sqids %)
          (s/keys :req-un [::instance ::alphabet ::min-length ::block-list]))
   #(gen/fmap sqids (s/gen ::options))))

(s/def ::numbers (s/coll-of nat-int? :kind sequential?))

(s/def ::id
  (s/with-gen string?
              #(gen/fmap
                (fn [[record numbers]]
                  (encode record numbers))
                (gen/tuple (s/gen ::record) (s/gen ::numbers)))))

(def default-options
  (conform! ::options
            {:alphabet   org.sqids.Sqids$Builder/DEFAULT_ALPHABET
             :min-length org.sqids.Sqids$Builder/DEFAULT_MIN_LENGTH
             :block-list (set org.sqids.Sqids$Builder/DEFAULT_BLOCK_LIST)}))

(defn sqids
  ([] (sqids {}))
  ([options]
   (conform! ::options options)
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
          map->Sqids
          (conform! ::record)))))

(s/fdef sqids
  :args (s/alt :nullary (s/cat)
               :unary   (s/cat :options ::options))
  :ret ::record)

(defn ^:no-gen encode
  [record numbers]
  (encode* record numbers))

(s/fdef encode
  :args (s/cat :record ::record :numbers ::numbers)
  :ret  ::id
  :fn   (fn [{:keys [ret] {:keys [record numbers]} :args}]
          (= numbers (decode record ret))))

(defn ^:no-gen decode
  [record id]
  (decode* record id))

(s/fdef decode
  :args (s/cat :record ::record :id ::id)
  :ret  ::numbers
  :fn   (fn [{:keys [ret] {:keys [record]} :args}]
          (= ret (->> ret
                      (encode record)
                      (decode record)))))
