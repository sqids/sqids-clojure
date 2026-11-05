(ns org.sqids.clojure.parity-evaluator
  (:require
    [org.sqids.clojure :as sqids]))

(defn response
  "Evaluates one JSON-shaped request on either platform; exception text is language-specific."
  [{:keys [op options numbers sqid]}]
  (try
    (let [config (sqids/sqids (cond-> options
                                (contains? options :block-list) (update :block-list set)))]
      {:value (case op
                "roundtrip" (let [id (sqids/encode config numbers)]
                              {:sqid id :numbers (sqids/decode config id)})
                "encode" (sqids/encode config numbers)
                "decode" (sqids/decode config sqid)
                "sqids" "initialized")})
    (catch #?(:clj Exception :cljs :default) _
      {:error true})))
