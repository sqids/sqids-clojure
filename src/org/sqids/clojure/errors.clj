(ns org.sqids.clojure.errors
  (:require
    [org.sqids.clojure.results :as results]))

(defmacro throw-edge-error!
  "Throws an edge-facing exception from a non-throwing results envelope."
  [result]
  (let [result-sym     (gensym "result__")
        message-sym    (gensym "message__")
        details-sym    (gensym "details__")
        exception-sym  (gensym "exception__")
        ;; CLJS macro expansion provides `:ns` in `&env`; CLJ macro expansion does not.
        edge-ex-info-form (if (contains? &env :ns)
                            `(ex-info ~message-sym (assoc ~details-sym :cause ~exception-sym))
                            `(ex-info ~message-sym ~details-sym ~exception-sym))]
    `(let [~result-sym ~result
           ~message-sym (or (::results/message ~result-sym) "Sqids operation failed")
           ~details-sym (or (::results/details ~result-sym) {})
           ~exception-sym (::results/exception ~result-sym)]
       (throw (if ~exception-sym
                ~edge-ex-info-form
                (ex-info ~message-sym ~details-sym))))))

(defmacro unwrap-or-throw!
  "Returns an ok value or throws the corresponding edge-facing exception."
  [result]
  `(let [result# ~result]
     (if (= ::results/ok (::results/status result#))
       (::results/value result#)
       (throw-edge-error! result#))))
