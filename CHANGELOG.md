# Changelog

## [0.11.0] - 2026-07-21
### Added
- Added the `:owner-project-access` filter to `api-key-list`.

## [0.10.0] - 2026-07-21
### Changed
- Upgraded `com.openai/openai-java` to 4.43.0 and added `:owner-project-access`
  to project API key maps.

## [0.9.0] - 2026-07-16
### Added
- Added the `openai.realtime` namespace with Realtime WebSocket sessions,
  client-secret, session, transcription, and translation helpers, and SIP call
  control.
- Expanded Responses tool coverage with image generation, computer,
  shell/local shell, apply patch, custom, tool search, and MCP approval tools,
  plus their call-output input items.
- Added lossless conversion for all Responses output-item variants.
- Added normalization for the full Responses streaming-event surface.
- Added a structured-output helper that parses `json_schema` response text and
  validates it against the requested schema.

## [0.8.0] - 2026-07-11
### Changed
- **BREAKING (admin):** Admin API functions now take positional resource IDs
  (project, group, user, role, …) followed by an optional kebab-case opts map,
  replacing the single params map used in 0.7.0.
- Reimplemented the Admin API and curated every service response converter as
  hand-written, type-hinted interop returning present-only kebab-case maps;
  removed the runtime-reflection admin engine and generic JSON-dump conversion
  (retained only for webhook event unwrapping).
- Strengthened no-network unit-test coverage across the service namespaces.

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
