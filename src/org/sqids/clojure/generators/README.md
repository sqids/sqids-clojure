# Generator Namespaces

These namespaces hold `clojure.spec` generator helpers that are mirrored from
the runtime namespaces:

- `alphabet.cljc`
- `block_list.cljc`
- `init.cljc`
- `encoding.cljc`
- `decoding.cljc`

The owning runtime namespaces in `src/org/sqids/clojure/` still define the
actual specs and behavior. This directory exists to keep the runtime files
focused on algorithm and validation logic while giving generator-heavy code a
single place to live.

When changing a spec generator:

1. Update the mirrored generator namespace here.
2. Keep the owning runtime spec in sync.
3. Update any generator-focused tests, especially
   `test/org/sqids/clojure/decoding_test.cljc`.
