(ns org.sqids.clojure.encoding
  (:require
    [clojure.string :as str]
    [org.sqids.clojure.alphabet :as alphabet]))

(def default-min-length
  0)

(defn ^:private ->id
  [num alphabet]
  (loop [num num
         id  ()]
    (let [id  (conj id (nth alphabet (mod num (count alphabet))))
          num (quot (/ num (count alphabet)) 1)]
      (if (> num 0)
        (recur num id)
        id))))

(defn ^:private blocked?
  [{:keys [block-list]} id]
  (let [id (str/lower-case id)]
    (some (fn [word]
            (and
              (<= (count word) (count id))
              (or (and (or (<= (count id) 3) (<= (count word) 3))
                       (= id word))
                  (and (re-find #"\d" word)
                       (or (str/starts-with? id word)
                           (str/ends-with? id word)))
                  (str/includes? id word))))
          block-list)))

(defn ^:private encode-prefix
  [{:keys [alphabet]} numbers increment]
  (let [alpha-len
        (count alphabet)

        offset
        (mod
          (reduce-kv
            (fn [acc i v]
              (let [j (mod v alpha-len)]
                (+ acc i (alphabet/nth-char-code alphabet j))))
            (count numbers)
            numbers)
          alpha-len)

        offset
        (mod (+ offset increment) alpha-len)

        alphabet
        (alphabet/cut alphabet offset)

        prefix
        (first alphabet)

        alphabet
        (alphabet/reverse-str alphabet)]

    (loop [v        [prefix]
           alphabet alphabet
           index    0]
      (let [v     (into v (->id (nth numbers index) (subs alphabet 1)))
            index (inc index)]
        (if (< index (count numbers))
          (recur (conj v (first alphabet))
                 (alphabet/consistent-shuffle alphabet)
                 index)
          [v alphabet])))))

(defn ^:private pad-to-min-length
  [{:keys [alphabet min-length]} v]
  (loop [alphabet alphabet
         ret      (conj v (first alphabet))]
    (if (<= min-length (count ret))
      ret
      (let [end      (min (- min-length (count ret)) (count alphabet))
            alphabet (alphabet/consistent-shuffle alphabet)
            ret      (into ret (subs alphabet 0 end))]
        (recur alphabet ret)))))

(defn ^:private encode-suffix
  [{:keys [min-length] :as s} v]
  (apply str (if (<= min-length (count v))
               v
               (pad-to-min-length s v))))

(defn ^:private encode*
  [{:keys [sqids] :as options}]
  (let [numbers (vec (:numbers options))]
    (loop [increment 0]
      (when (> increment (count (:alphabet sqids)))
        (throw (ex-info "Reached max attempts to re-generate the ID" options)))

      (let [[v alphabet]
            (encode-prefix sqids numbers increment)

            id
            (encode-suffix (assoc sqids :alphabet alphabet) v)]

        (if (blocked? sqids id)
          (recur (inc increment))
          id)))))

(defn encode
  [{:keys [numbers] :as options}]
  (if (empty? numbers)
    ""
    (encode* options)))
