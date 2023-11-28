(ns sqids

  (:import (org.sqids Sqids Sqids$Builder)))

(def default-alphabet "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn build [{:keys [min-length alphabet] :or {min-length 0 alphabet default-alphabet}}]
  (-> (Sqids$Builder.)
      (.minLength min-length)
      (.alphabet alphabet)
      (.build)))

(def default-generator ^Sqids (build {:min-length 0}))

(defn encode
  ([ints]
   (encode default-generator ints))
  ([generator ints]
   (.encode ^Sqids generator ints)))

(defn decode
  ([sqid-str]
   (decode default-generator sqid-str))
  ([generator sqid-str]
   (.decode ^Sqids generator sqid-str)))
