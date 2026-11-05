# [`sqids-clojure`](https://sqids.org/clojure)

[![sqids-clojure on Clojars](https://img.shields.io/clojars/v/org.sqids/sqids-clojure.svg)](https://clojars.org/org.sqids/sqids-clojure) [![workflow status](https://github.com/sqids/sqids-clojure/actions/workflows/clojure.yml/badge.svg)](https://github.com/sqids/sqids-clojure/actions/workflows/clojure.yml)

Sqids (pronounced "squids") is a small library that lets you generate
YouTube-looking IDs from numbers. It's good for link shortening, fast &
URL-safe ID generation and decoding back into numbers for quicker database
lookups.

`sqids-clojure` supports both Clojure and ClojureScript with a shared pure
implementation in this repository.

## Getting started

[CLI/`deps.edn`](https://clojure.org/reference/deps_and_cli) dependency
information (replace with the latest version from
[Clojars](https://clojars.org/org.sqids/sqids-clojure)):

```clojure
;; maven
org.sqids/sqids-clojure {:mvn/version "1.1.0"}
```

[Leiningen](https://leiningen.org/) dependency information:

```clojure
[org.sqids/sqids-clojure "1.1.0"]
```

After installation, require the `org.sqids.clojure` namespace:

```clojure
(require '[org.sqids.clojure :as sqids])
```

## Examples

Simple encode & decode:

```clojure
(def sqids
  (sqids/sqids))

(def id
  (sqids/encode sqids [1 2 3])) ; "86Rf07"

(def numbers
  (sqids/decode sqids id)) ; [1 2 3]
```

Create an instance once and reuse it. `sqids` validates an options map and returns
a non-associative `deftype` instance, which `encode` and `decode` accept directly.
Keep your options separately if you need to serialize them or construct another
instance with different settings:

```clojure
(def options {:block-list #{}})
(def unfiltered (sqids/sqids options))
(def padded (sqids/sqids (assoc options :min-length 10)))
```

The instance does not support `assoc`; its fields are implementation details.
Serialize the options map, not the instance. This changes the previous map return
type: remove `:instance` lookups and pass the constructor's result directly to
`encode` and `decode`. Raw options and maps wrapping an instance are rejected.

Inputs must be sequential collections of non-negative integers. The JVM supports
arbitrary-size integers; ClojureScript supports integers up to
`Number.MAX_SAFE_INTEGER` (9,007,199,254,740,991). Decoding returns `[]` for IDs
containing characters outside the alphabet, or values outside the ClojureScript
safe-integer range. Invalid argument types and exhausted blocklist retries throw
`ExceptionInfo` with explanatory data.

> **Note**
> 🚧 Because of the algorithm's design, **multiple IDs can decode back into the
> same sequence of numbers**. If it's important to your design that IDs are
> canonical, you have to manually re-encode decoded numbers and check that the
> generated ID matches.

Enforce a _minimum_ length for IDs:

```clojure
(def sqids
  (sqids/sqids {:min-length 10}))

(def id
  (sqids/encode sqids [1 2 3])) ; "86Rf07xd4z"

(def numbers
  (sqids/decode sqids id)) ; [1 2 3]
```

Randomize IDs by providing a custom alphabet:

```clojure
(def sqids
  (sqids/sqids {:alphabet "FxnXM1kBN6cuhsAvjW3Co7l2RePyY8DwaU04Tzt9fHQrqSVKdpimLGIJOgb5ZE"}))

(def id
  (sqids/encode sqids [1 2 3])) ; "B4aajs"

(def numbers
  (sqids/decode sqids id)) ; [1 2 3]
```

Override the blocklist:

```clojure
(def sqids
  (sqids/sqids {:block-list #{"86Rf07"}}))

(def id
  (sqids/encode sqids [1 2 3])) ; "se8ojk"

(def numbers
  (sqids/decode sqids id)) ; [1 2 3]
```

Three-character blocklist words match only whole IDs. Longer words containing
digits match only at either end; other longer words match anywhere. Words shorter
than three characters are ignored.

## Development

Use Java 21, Clojure CLI, Node.js 22+, npm 11.10+, and Python 3.11+. On macOS,
`bin/setup` installs the Homebrew prerequisites. Then run:

```bash
python3 -m venv .venv
source .venv/bin/activate
export PIP_CONSTRAINT="$PWD/requirements.txt"
export PIP_BUILD_CONSTRAINT="$PIP_CONSTRAINT"
pip install -r requirements.txt
npm ci
bin/kaocha
bin/benchmark
bin/benchmark-node
pre-commit run --all-files
```

Dependency updates use stable releases at least 14 days old, with documented
exceptions for security fixes. `.npmrc` applies this age window when resolving
npm dependencies, disables lifecycle scripts, and requires a supporting npm
version. Formatters use the same npm lockfile as the development tools. Python
pins also constrain isolated hook environments. CI actions use commit SHAs and
tool versions are explicit; keep those pins synchronized with `deps.edn`.
Clojure lint and formatting hooks run the pinned aliases from `deps.edn`.
The scoped `markdownlint-cli2` overrides select aged security fixes for KaTeX,
markdown-it, and smol-toml that its dependency ranges would otherwise exclude.
Remove them when an age-qualified upstream release includes those fixes.

`bin/kaocha` runs JVM and ClojureScript tests, including fixed vectors, invalid
inputs, and seeded roundtrip properties. Coverage includes all runtime code,
with a 90% minimum; reports are written to `target/coverage/` and JUnit XML to
`target/test-results/junit.xml`.

Run the same differential corpus against this library on the JVM and Node.js
and the upstream TypeScript implementation. The Node runner uses advanced
ClojureScript optimization:

```bash
SQIDS_SPEC_DIR=/path/to/sqids-spec bin/parity
```

Install the reference repository's dependencies with `npm ci` first. The corpus
covers the shared JavaScript-safe integer domain, custom alphabets, padding,
blocklist retries, alternate encodings, malformed IDs, and invalid options.

`bin/benchmark` measures JVM time and allocation with reused configurations.
CI enforces per-call allocation budgets for single-digit, multi-digit, and
128-bit integer inputs, plus linear allocation growth for many-number inputs.

`bin/benchmark-node` measures the advanced-compiled public API on Node, including
multi-digit and maximum-safe-integer inputs. It reports the default and empty
blocklists separately to expose filtering cost. Both benchmarks consume results
after warmup and report median timings; CI does not gate on noisy wall-clock
limits. Compare timings using the same runtime and hardware.

All runtime code lives in `src/org/sqids/clojure.cljc`, with private implementation
helpers behind the public API. Behavior tests and generators live in
`test/org/sqids/clojure_test.cljc`; the parity harness stays under
`test/org/sqids/clojure/`, and benchmarks live under `dev/`.
There are no third-party runtime dependencies. Temporary mutable buffers are
owned by individual calls; instances can be shared across threads.

Refresh the bundled default blocklist with `bin/update-blocklist`. Missing
blocklist data is an error, so a broken package cannot silently disable filtering.
Use `{:block-list #{}}` to disable filtering explicitly.

`clojure -T:build jar` builds the source JAR; it does not run the tests.

## License

[MIT](LICENSE)
