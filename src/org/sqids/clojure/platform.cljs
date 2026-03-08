(ns org.sqids.clojure.platform)

(defn char-code
  "Returns the UTF-16 code unit for a JS character."
  [^js c]
  (.charCodeAt c 0))

(def max-value
  "Maximum non-negative safe integer representable in JavaScript."
  js/Number.MAX_SAFE_INTEGER)

(defn in-range?
  "Checks whether `n` is a non-negative JavaScript safe integer."
  [n]
  (and (number? n)
       (js/Number.isSafeInteger n)
       (<= 0 n max-value)))

(def +-safe
  "Addition function used by decode arithmetic on CLJS."
  +)

(def *-safe
  "Multiplication function used by decode arithmetic on CLJS."
  *)

(def nat-int-random-max
  "Upper bound used for random natural integer generators."
  max-value)

(def nat-int-edge-values
  "Boundary values used by natural integer generators."
  [0
   1
   2
   10
   255
   1024
   9007199254740990
   9007199254740991])

(def decode-overflow-sqid
  "Known overflow Sqid for CLJS safe-integer decoding checks."
  "pup591lWlB")

(defn decode-overflow-sqid-available?
  "Returns true when this runtime provides a known decode-overflow Sqid."
  []
  (some? decode-overflow-sqid))

(defn decode-overflow-sqid?
  "Returns true when `sqid` matches the runtime decode-overflow fixture."
  [sqid]
  (and (decode-overflow-sqid-available?)
       (= decode-overflow-sqid sqid)))

(defn decode-step
  "Advances decode arithmetic by one digit step, returning nil on overflow."
  [acc base idx]
  (let [next-value (+-safe (*-safe acc base) idx)]
    (when (in-range? next-value)
      next-value)))

(defn exception-message
  "Extracts a stable string message from an exception-like value."
  [cause]
  (or (.-message cause)
      (ex-message cause)
      (str cause)))

(defn try-call
  "Evaluates `thunk`, delegating caught exceptions to `on-error`."
  [thunk on-error]
  (try
    (thunk)
    (catch :default cause
      (on-error cause))))
