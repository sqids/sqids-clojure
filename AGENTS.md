# Repository Guidelines

## Project Structure & Module Organization

- Core library code lives in `src/org/sqids/`.
- Cross-platform API is in `clojure.cljc`; specs are colocated in owning `.cljc` namespaces (`alphabet`, `block_list`, `encoding`, `decoding`, `init`, `results`).
- Spec generator helpers live in mirrored namespaces under `src/org/sqids/clojure/generators/`.
- Platform-specific helper functions are in `platform.clj` (JVM) and `platform.cljs` (CLJS).
- Sqids algorithm logic is implemented in-repo (`alphabet.cljc`, `encoding.cljc`, `decoding.cljc`, `block_list.cljc`, `init.cljc`).
- Bundled data assets are in `resources/` (for example `resources/org/sqids/clojure/blocklist.json`).
- Tests are under `test/org/sqids/clojure/*_test.cljc`.
- The upstream parity harness lives in `test/org/sqids/clojure/parity*.clj` with its Node helper in `test-resources/`.
- Tooling scripts are in `bin/` (`setup`, `kaocha`, `parity`, `repl`, `_clj-kondo`, `_cljstyle`, `update-blocklist`).

## Build, Test, and Development Commands

- `bin/setup`: installs local prerequisites (`brew bundle`, `npm install`) on macOS/Homebrew setups.
- `bin/kaocha`: runs all Kaocha suites (JVM `clojure.test`, automatic `clojure.spec.test.check`, and CLJS) with coverage output in `target/coverage/` and JUnit XML in `target/test-results/junit.xml`.
- `SQIDS_SPEC_DIR=/path/to/sqids-spec bin/parity`: runs the JVM-only upstream parity check against a checked-out `sqids-spec` reference repo.
- `bin/repl clj` / `bin/repl cljs`: starts interactive REPLs.
- `bin/update-blocklist`: refreshes `resources/org/sqids/clojure/blocklist.json` from `sqids-spec`.
- `pre-commit run --all-files`: runs local hooks; CI mirrors this as `SKIP=kaocha-test pre-commit run --all-files` followed by `bin/kaocha`.
- `clojure -T:build jar` and `clojure -T:build install`: build and install the jar locally.

## Project Patterns

- Treat `AGENTS.md` as a repo table of contents, not an encyclopedia: point to the system-of-record files for behavior, build, tests, lint, and generated data before adding new prose here.
- Prefer checked-in repo knowledge over chat or PR context; if code, tests, and docs disagree, the repository wins and stale guidance should be fixed in the same change.
- Treat `bin/kaocha` as the single test entrypoint for local runs, pre-commit, and CI; avoid re-introducing split CLJ/CLJS wrappers.
- Keep `tests.edn` as the source of truth for suite and report behavior (`:unit`, `:cljs`, `:generative-fdef-checks`, cloverage, JUnit XML target).
- Keep test automation in sync: when pre-commit hook IDs or test commands change, update `.github/workflows/clojure.yml` (`SKIP=...`) in the same commit.
- Prefer local/system hooks and wrappers over Docker hooks (`clj-kondo`, `cljstyle`, `shfmt`, `prettier`); avoid adding Docker-based hook runners.
- Move invariants into mechanical enforcement whenever possible; prefer `clj-kondo`, `cljstyle`, pre-commit, Kaocha, and CI checks over reviewer memory or PR prose.
- Keep algorithm code total in internal namespaces: only public `org.sqids.clojure/{sqids,encode,decode}` should throw.
- Model internal success/failure with `org.sqids.clojure.results` envelopes and staged `results/conform`, `results/bind`, `results/attempt` pipelines.
- Keep tool versions pinned in project config (`deps.edn`, `.pre-commit-config.yaml`); CI may use `latest` for bootstrap tools, but lint/format/test versions should remain explicit.
- Treat `resources/org/sqids/clojure/blocklist.json` as generated data; update it via `bin/update-blocklist` rather than manual edits.
- Add nested `AGENTS.md` files only when a subtree has materially different rules or maintenance needs; keep them short, additive, and scoped to local deltas rather than copying the root guide.
- Pair every `AGENTS.md` with a human-facing `README.md` in the same scope. `AGENTS.md` directs agent behavior; `README.md` explains layout, intent, and workflows for humans.
- When adding a new subsystem, ship code, tests, lint/config enforcement, docstrings, and navigation docs together.
- Keep docs aligned with tooling changes: update `README.md` and this file in the same PR when commands or test flow change.
- When behavior changes, update tests, docstrings, and README examples in the same commit so the contract stays synchronized.
- Preserve public API behavior for `sqids`, `encode`, and `decode`; behavior changes must include deterministic tests and release notes updates.
- Keep upstream parity checks in the shared JS-safe integer domain; this library intentionally supports larger JVM integers than `sqids-spec` can represent.

## Coding Style & Naming Conventions

- Follow `.cljstyle`: 2-space indentation and one blank padding line between top-level forms.
- Run formatting before commits: `bin/_cljstyle fix`.
- Lint with pre-commit (includes `clj-kondo`, `cljstyle`, `kaocha-test`, `shellcheck`, `shfmt`, `markdownlint-cli2`, `prettier`, and a clean `git-diff` check).
- `clj-kondo` is intentionally strict in `.clj-kondo/config.edn`; run `bin/_clj-kondo --lint src test bin/update-blocklist build.clj deps.edn tests.edn shadow-cljs.edn` before pushing.
- Every `def`, `defn`, and `defmacro` needs a high-quality docstring. Treat docstrings as living contracts: explain purpose, inputs, return shape, invariants, and failure semantics when they are not obvious from the name alone.
- Keep docstring enforcement strict. If `clj-kondo` stops catching missing docstrings on new function-like forms, tighten the linter or hooks instead of weakening the rule.
- Namespace/file naming follows Clojure conventions: `kebab-case` namespaces and `*_test.cljc` test files.

## Testing Guidelines

- Primary framework: `clojure.test` executed by Kaocha; generative spec checks run via a `:kaocha.type/spec.test.check` suite.
- Coverage is enforced at 100% via `:cloverage/opts :fail-threshold`; CI artifacts include `target/coverage/lcov.info` and `target/coverage/codecov.json`.
- Add tests beside related behavior in `test/org/sqids/clojure/`.
- Keep tests deterministic and cover both encode/decode behavior and invalid-input paths.
- Before opening a PR, run `bin/kaocha`.

## Commit & Pull Request Guidelines

- Match existing history: short, imperative commit subjects (for example `Fix sqids-javascript link`, `Improve caching`).
- Keep commits focused; separate refactors from behavior changes when possible.
- PRs should include: purpose, key changes, and verification steps/commands run.
- Link relevant issues when applicable and update `CHANGELOG.md` for release-facing changes.
