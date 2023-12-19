(ns org.sqids.clojure.block-list
  (:require
    #?@(:clj
        [[clojure.data.json :as json]
         [clojure.java.io :as io]])
    [clojure.set :as set]
    [clojure.string :as str])
  #?(:cljs
     (:require-macros
       [org.sqids.clojure.block-list :refer [read-default]])))

(def min-word-length
  3)

#?(:clj
   (defmacro read-default
     []
     (-> "blocklist.json"
         io/resource
         io/reader
         json/read
         set)))

(def default
  (read-default))

(def ^:private base-xf
  (comp
    (map str/lower-case)
    (filter #(>= (count %) min-word-length))))

(defn ^:private make-subset-xf
  [alphabet]
  (let [alpha-set (set (str/lower-case alphabet))]
    (filter #(set/subset? (set %) alpha-set))))

(defn ^:private make-xf
  [alphabet]
  (comp base-xf (make-subset-xf alphabet)))

(defn remove-invalid-words
  [block-list alphabet]
  (into #{} (make-xf alphabet) block-list))
