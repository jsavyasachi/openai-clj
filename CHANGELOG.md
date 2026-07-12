# Changelog

## [0.7.0] - 2026-07-11
### Added
- Added stable images, audio, moderations, legacy completions, vector stores,
  uploads, containers, conversations, fine-tuning, evals, skills, videos,
  webhooks, and organization/project admin APIs.
- Added stored Chat Completions CRUD and model deletion.

### Changed
- Upgraded `com.openai/openai-java` from 4.41.0 to 4.42.0.
- Added GPT-5.6-sol reasoning mode, prompt-cache options, programmatic tool
  calling, and cache-write token usage.
- Admin API and all service namespaces use hand-written typed interop with
  curated, present-only response maps and idiomatic positional resource IDs
  (no runtime reflection or generic JSON-dump conversion outside webhooks).

## [0.6.0] - 2026-07-10
### Added
- Added Chat Completions API compatibility support, including create and streaming helpers.

## [0.5.2] - 2026-07-09
### Changed
- Reorganized the README into a cljdoc article tree under `doc/` (Tools, Streaming, Embeddings/Files/Batches, Azure, Responses & Errors, Migrating). Documentation content is unchanged; no API changes.

All notable changes to this project are documented here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project adheres to [Semantic Versioning](https://semver.org/).

## [0.5.1] - 2026-07-09
### Fixed
- POM now includes the project description, homepage URL, and full SCM connection metadata, so Clojars shows a description/homepage and cljdoc has complete source-link data.
