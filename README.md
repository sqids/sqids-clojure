# [`sqids-clojure`](https://sqids.org/clojure)

Sqids (pronounced "squids") is a small library that lets you generate
YouTube-looking IDs from numbers. It's good for link shortening, fast &
URL-safe ID generation and decoding back into numbers for quicker database
lookups.

`sqids-clojure` wraps [`sqids-java`](https://github.com/sqids/sqids-java),
delegating most of the implementation to `sqids-java`. `sqid-clojure`'s version
follows `sqids-java`'s major and minor versions.

If you notice an issue with decoding and encoding, please refer to `sqids-java`
for possible issues.

## Getting started

`sqids-clojure` is currently **unreleased**. The following dependency
information is an example, but will be updated in the future upon release.

[CLI/`deps.edn`](https://clojure.org/reference/deps_and_cli) dependency
information:

```clojure
org.sqids/sqids-clojure {:mvn/version "0.1.0-SNAPSHOT"}
```

[Leiningen](https://leiningen.org/) stable dependency information:

```clojure
[org.clojure/tools.namespace "0.1.0-SNAPSHOT"]
```

After installation, require `sqids-clojure`:

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
