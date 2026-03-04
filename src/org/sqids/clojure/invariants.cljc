(ns org.sqids.clojure.invariants
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.decoding :as decoding]
    [org.sqids.clojure.encoding :as encoding]
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.results :as results]))

(defn ^:private valid-alphabet-characters?
  "Returns true when every Sqid character exists in the Sqids alphabet."
  [sqids-config sqid]
  (set/subset? (set sqid) (set (:alphabet sqids-config))))

(defn ^:private encode-roundtrip?
  "Returns true when decoding `sqid` reproduces the original `numbers`."
  [sqids numbers sqid]
  (let [decode-result (decoding/decode sqids sqid)]
    (and (results/ok? decode-result)
         (= (vec numbers) (::results/value decode-result)))))

(defn encode-result-consistent?
  "Checks semantic invariants for `encoding/encode` args and results."
  [sqids-config numbers ret]
  (boolean
    (let [initialized-result (init/ensure-initialized sqids-config)
          initialized?       (results/ok? initialized-result)
          valid-numbers?     (s/valid? ::encoding/nat-ints numbers)]
      (if (results/ok? ret)
        (and initialized?
             valid-numbers?
             (let [sqids        (::results/value initialized-result)
                   sqid-value   (::results/value ret)
                   alphabet-set (set (:alphabet sqids))]
               (if (empty? numbers)
                 (= "" sqid-value)
                 (and (not (empty? sqid-value))
                      (<= (:min-length sqids) (count sqid-value))
                      (every? alphabet-set sqid-value)
                      (not (block-list/blocked-id? (::block-list/index sqids)
                                                   sqid-value))
                      (encode-roundtrip? sqids numbers sqid-value)))))
        (or (not initialized?)
            (not valid-numbers?)
            (not= ::results/invalid-input (::results/code ret)))))))

(defn decode-result-consistent?
  "Checks semantic invariants for `decoding/decode` args and results.

  This stays one-way on purpose so decode fdef checks do not recursively invoke
  encode fdef checks while `encode` is validating its stronger round-trip
  property."
  [sqids-config sqid ret]
  (boolean
    (let [initialized-result (init/ensure-initialized sqids-config)
          initialized?       (results/ok? initialized-result)]
      (if (results/ok? ret)
        (and initialized?
             (string? sqid)
             (let [sqids          (::results/value initialized-result)
                   decoded-values (::results/value ret)]
               (if (valid-alphabet-characters? sqids sqid)
                 (if (empty? sqid)
                   (empty? decoded-values)
                   true)
                 (empty? decoded-values))))
        (or (not initialized?)
            (not (string? sqid)))))))

(s/fdef encoding/encode
  :args (s/cat :sqids-config ::encoding/sqids-input
               :numbers ::encoding/numbers-input)
  :ret ::encoding/encode-result
  :fn (fn [{:keys [args ret]}]
        (let [{:keys [sqids-config numbers]} args]
          (encode-result-consistent? sqids-config
                                     numbers
                                     (s/unform ::encoding/encode-result ret)))))

(s/fdef decoding/decode
  :args ::decoding/decode-call-args
  :ret ::decoding/decode-result
  :fn (fn [{:keys [args ret]}]
        (let [{:keys [sqids-config sqid]} args]
          (decode-result-consistent? sqids-config
                                     sqid
                                     (s/unform ::decoding/decode-result ret)))))
