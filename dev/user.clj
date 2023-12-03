(ns user
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.namespace.repl
     :refer [refresh refresh-all]
     :rename {refresh r refresh-all ra}]
    [expound.alpha :as expound]
    [kaocha.repl
     :refer [run run-all]
     :rename {run t run-all ta}]
    [orchestra.spec.test :as orchestra]))

;; Alter the root *explain-out* binding.
(alter-var-root #'s/*explain-out* (constantly expound/printer))

;; Set *explain-out* if it's thread-bound, which is necessary for some REPL
;; configurations.
(when (thread-bound? #'s/*explain-out*)
  (set! s/*explain-out* expound/printer))

(set! *warn-on-reflection* true)

(defn refresh-and-test
  ([refresh-fn kaocha-fn]
   (refresh-and-test refresh-fn kaocha-fn {}))
  ([refresh-fn kaocha-fn kaocha-opts]
   (orchestra/unstrument)
   (refresh-fn)
   (orchestra/instrument)
   (kaocha-fn kaocha-opts)))

(defn rt
  [& args]
  (apply refresh-and-test r t args))

(defn rat
  [& args]
  (apply refresh-and-test ra t args))

(defn rta
  [& args]
  (apply refresh-and-test r ta args))

(defn rata
  [& args]
  (apply refresh-and-test ra ta args))

(defn ratu
  [& args]
  (apply refresh-and-test ra t :unit args))

(defn ratg
  [& args]
  (apply refresh-and-test ra t :generative-fdef-checks args))
