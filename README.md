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

## Development

Run the local checks before opening a PR:

```bash
bin/setup
bin/_clj-kondo --lint src test bin/update-blocklist build.clj deps.edn tests.edn shadow-cljs.edn
bin/kaocha
pre-commit run --all-files
```

Run the upstream parity check against a checked-out `sqids-spec` repository:

```bash
SQIDS_SPEC_DIR=/path/to/sqids-spec bin/parity
```

`bin/setup` is optimized for macOS/Homebrew (`brew bundle` + `npm install`).
On other platforms, install Java, Clojure CLI, Babashka, Node.js, Python, and
`pre-commit` manually, then run `npm install`.

`bin/kaocha` runs all configured Kaocha suites (`clojure.test`, automatic
`clojure.spec.test.check`, and ClojureScript). It enforces 100% cloverage on
the tracked runtime namespaces and writes reports to `target/coverage/`:
`index.html`, `lcov.info`, and `codecov.json`. It also emits JUnit XML to
`target/test-results/junit.xml`.

`bin/parity` is a separate JVM-only check. It uses `clojure.spec` generators
within the shared JavaScript-safe integer domain, evaluates the same cases with
the checked-out `sqids-spec` TypeScript implementation, and compares those
results to this library.

Generator-heavy `clojure.spec` helpers live in mirrored namespaces under
`src/org/sqids/clojure/generators/`, while the owning runtime namespaces keep
the specs and algorithm code.

Refresh the default bundled blocklist from the Sqids spec repository:

```bash
bin/update-blocklist
```

This updates `resources/org/sqids/clojure/blocklist.json`.

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

Prevent specific words from appearing anywhere in the auto-generated IDs:

```clojure
(def sqids
  (sqids/sqids {:block-list #{"86Rf07"}}))

(def id
  (sqids/encode sqids [1 2 3])) ; "se8ojk"

(def numbers
  (sqids/decode sqids id)) ; [1 2 3]
```

## License

[MIT](LICENSE)
