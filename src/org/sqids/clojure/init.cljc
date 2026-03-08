(ns org.sqids.clojure.init
  (:require
    [clojure.spec.alpha :as s]
    [org.sqids.clojure.alphabet :as alphabet]
    [org.sqids.clojure.block-list :as block-list]
    [org.sqids.clojure.generators.init :as init-generators]
    [org.sqids.clojure.results :as results]))

(def default-min-length
  "Default minimum output ID length."
  0)

(def min-length-limit
  "Maximum supported minimum output ID length."
  255)

(def default-options
  "Default Sqids options before user overrides."
  {:alphabet   alphabet/default
   :min-length default-min-length
   :block-list block-list/default-words})

(defn ^:private build-sqids-config
  "Builds initialized Sqids configuration from already-conformed options."
  [options]
  (let [merged-options      (merge default-options options)
        alphabet-option     (:alphabet merged-options)
        cleaned-options     {:alphabet   alphabet-option
                             :min-length (:min-length merged-options)
                             :block-list (block-list/remove-invalid-words
                                           (:block-list merged-options)
                                           alphabet-option)}
        initialized-options (update cleaned-options :alphabet alphabet/consistent-shuffle)]
    (assoc initialized-options
           ;; Internal cache for fast blocklist checks; not a public option key.
           ::block-list/index
           (block-list/build-index (:block-list initialized-options)))))

(defn sqids
  "Builds an initialized Sqids configuration and returns a result envelope."
  ([]
   (sqids {}))
  ([options]
   (-> (results/conform ::options options ::sqids-options-stage)
       (results/bind ::sqids-init-stage
                     (fn [conformed-options]
                       (results/ok (build-sqids-config conformed-options))))
       (results/bind ::sqids-ret-stage
                     (fn [sqids-config]
                       (results/conform ::sqids
                                        sqids-config
                                        ::sqids-ret-stage))))))

(defn ensure-initialized
  "Returns initialized Sqids configuration as a result envelope."
  [config]
  (if (and (map? config)
           (contains? config ::block-list/index))
    (results/conform ::sqids config ::ensure-initialized-ret-stage)
    (-> (results/conform ::options config ::ensure-initialized-options-stage)
        (results/bind ::ensure-initialized-stage
                      (fn [conformed-options]
                        (results/ok (build-sqids-config conformed-options))))
        (results/bind ::ensure-initialized-ret-stage
                      (fn [sqids-config]
                        (results/conform ::sqids
                                         sqids-config
                                         ::ensure-initialized-ret-stage))))))

(s/def ::options
  (s/with-gen
    (s/keys :opt-un [::alphabet/alphabet ::min-length ::block-list/block-list])
    #(init-generators/options min-length-limit)))

(s/def ::sqids
  (s/with-gen
    (s/keys :req-un [::alphabet/alphabet ::min-length ::block-list/block-list])
    #(init-generators/sqids min-length-limit)))

(s/def ::min-length
  (s/int-in 0 (inc min-length-limit)))

(s/def ::sqids-result
  (s/or :ok (results/ok-result-for ::sqids)
        :error ::results/error-result))

(s/def ::options-input
  (s/with-gen
    any?
    #(init-generators/options-input min-length-limit)))

(s/def ::sqids-input
  (s/with-gen
    any?
    #(init-generators/sqids-input min-length-limit)))

(s/fdef sqids
  :args (s/alt :nullary (s/cat)
               :unary (s/cat :options ::options-input))
  :ret ::sqids-result)

(s/fdef ensure-initialized
  :args (s/cat :config ::sqids-input)
  :ret ::sqids-result)
