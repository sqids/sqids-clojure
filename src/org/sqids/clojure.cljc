(ns org.sqids.clojure
  (:require
    [org.sqids.clojure.decoding :as decoding]
    [org.sqids.clojure.encoding :as encoding]
    #?@(:clj
        [[org.sqids.clojure.errors :as errors]])
    [org.sqids.clojure.init :as init]
    [org.sqids.clojure.results :as results])
  #?(:cljs
     (:require-macros
       [org.sqids.clojure.errors :as errors])))

(def default-options
  "Default Sqids options after initialization."
  (::results/value (init/sqids init/default-options)))

(defn sqids
  "Builds an immutable Sqids configuration map.

  Supported options:
  - `:alphabet` String with unique single-byte chars and length >= 3.
  - `:min-length` Integer in [0, 255].
  - `:block-list` Set of words to exclude from encoded IDs.

  If `:block-list` is omitted, the default Sqids blocklist is used.
  If `:block-list` is an empty set, blocklist filtering is disabled."
  ([]
   (sqids {}))
  ([options]
   (-> options
       init/sqids
       errors/unwrap-or-throw!)))

(defn encode
  "Encodes a sequence of non-negative integers into a Sqid string."
  [sqids-config numbers]
  (-> (encoding/encode sqids-config numbers)
      errors/unwrap-or-throw!))

(defn decode
  "Decodes a Sqid string back into a vector of integers.

  Returns `[]` when the input contains characters outside the configured
  alphabet."
  [sqids-config sqid]
  (-> (decoding/decode sqids-config sqid)
      errors/unwrap-or-throw!))
