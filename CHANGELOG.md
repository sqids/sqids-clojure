# CHANGELOG

## Unreleased

## v1.1.0

- Updated development dependencies, compilers, build tools, and CI actions using
  a 14-day release-age window, with documented security exceptions. Pinned CI
  actions and toolchains, locked formatter dependencies, and constrained Python
  hook dependencies.
- Replaced Java/JavaScript runtime wrappers with a shared Clojure/ClojureScript
  implementation in `org.sqids.clojure`, with private implementation helpers and
  a bundled default blocklist.
- Changed `sqids` to return a reusable, non-associative `deftype` instance
  directly instead of a configuration map containing `:instance`. Pass that
  instance to `encode` and `decode`; keep options separately and construct a new
  instance to change settings. Raw options and wrapper maps are rejected.
- Added arbitrary-size JVM integer support and safe-integer checks on JavaScript
  encoding and decoding.
- Used per-call buffers and cursor-based decoding for linear allocation growth
  in the number of encoded values. Prefix assembly shares the buffer operations
  across JVM and JavaScript.
- Added advanced-compiled ClojureScript reference parity, public API properties,
  allocation budgets covering multi-digit and bigint inputs, and Node benchmarks.
- Consolidated behavior tests and generators into `org.sqids.clojure-test`.
- Missing bundled blocklist data now fails clearly instead of disabling filtering.

## v1.0.15

- Initial implementation
