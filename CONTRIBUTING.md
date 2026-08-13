# Contributing to openai-clj

Send bug reports, fixes, and focused feature contributions for `openai-clj`.

## Before you start

- For a change larger than a trivial fix, **open an issue first**. This lets us
  agree on the approach before you spend time.
- Check existing issues and pull requests to avoid duplicate work.

## Project layout

The library uses one `deps.edn` file. Source files are under `src/openai/`:

| Namespace | Purpose |
|---|---|
| `openai.types` | next.jdbc `ReadableColumn` / `SettableParameter` extensions for LIST / STRUCT / MAP |
| `openai.core` | datasource constructors, `read-parquet` / `read-csv`, `attach!`, extensions |

Code must stay reflection-free (`*warn-on-reflection*` is on). Expected
failures throw `ex-info` with a `:openai/error` key.

## Building and testing

Requires JDK 17+.

```bash
clojure -M:test            # full suite (Kaocha)
clojure -M:1.11:test       # Clojure 1.11 matrix cell
clojure -M:1.12:test       # Clojure 1.12 matrix cell
clojure -T:build jar       # build a jar
```

The full suite uses in-memory OpenAI. You do not need to download files or
start services.

Requirements for a change that can merge:

- **Tests first.** Add or update tests for the behavior you change; for a bug
  fix, include a regression test that fails before your fix and passes after.
- **Green build.** `clojure -M:test` passes and `src` compiles with **zero**
  reflection warnings (`*warn-on-reflection*` is on).
- **One scope.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Keep the subject in the imperative mood and under ~72 characters.
- Update `CHANGELOG.md` when your change is user-visible.
- Rebase on the current `main` before you open the pull request.

## License

By contributing, you agree that your contributions will be licensed under the
Eclipse Public License 2.0, the same license as this project (see `LICENSE`).
