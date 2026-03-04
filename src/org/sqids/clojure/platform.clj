(ns org.sqids.clojure.platform)

(defn char-code
  "Returns the integer character code for a JVM char."
  [c]
  (int c))

(def max-value
  "Upper-bound sentinel for JVM integer range checks."
  ##Inf)

(defn in-range?
  "Returns true on JVM, where integer values are effectively unbounded."
  [_]
  true)

(def +-safe
  "Overflow-safe addition for JVM integers."
  +')

(def *-safe
  "Overflow-safe multiplication for JVM integers."
  *')

(def nat-int-random-max
  "Upper bound used for random natural integer generators."
  1000000000)

(def nat-int-edge-values
  "Boundary values used by natural integer generators."
  [0N
   1N
   2N
   10N
   255N
   1024N
   9007199254740991N
   9007199254740992N
   18446744073709551616N
   340282366920938463463374607431768211455N])

(def decode-overflow-sqid
  "Known overflow Sqid for CLJS safe-integer decoding checks."
  nil)

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
  "Advances decode arithmetic by one digit step."
  [acc base idx]
  (+-safe (*-safe acc base) idx))

(defn exception-message
  "Extracts a stable string message from an exception-like value."
  [cause]
  (or (ex-message cause)
      (str cause)))

(defn try-call
  "Evaluates `thunk`, delegating caught exceptions to `on-error`."
  [thunk on-error]
  (try
    (thunk)
    (catch Exception cause
      (on-error cause))))
