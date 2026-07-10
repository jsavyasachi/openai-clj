# Migrating From wkok/openai-clojure

[wkok/openai-clojure](https://github.com/wkok/openai-clojure) wraps the Chat
Completions API over hand-rolled HTTP; openai-clj wraps the official
`com.openai/openai-java` SDK and the Responses API. The structural differences:

- **Explicit client.** wkok reads `OPENAI_API_KEY` ambiently and takes
  credentials in a per-call options map. openai-clj builds a client once
  (`(openai/client)` also reads `OPENAI_API_KEY`) and passes it as the first
  argument to every function.
- **Responses, not Chat Completions.** `:messages` becomes `:input` (a plain
  string or a vector of message items); the `:role "system"` message becomes
  `:instructions`; multi-turn state can use `:previous-response-id` instead of
  resending history.
- **Flat function tools.** wkok nests `{:type "function" :function {:name ...}}`;
  openai-clj flattens to `{:type :function :name ... :parameters ...}`, and tool
  call results go back as `:function-call-output` input items.
- **Streaming is a function, not a flag.** wkok's `:stream true` + `:on-next`
  becomes `(openai/stream client params callback)` or `stream-text` for text
  deltas only.
- **Errors are `ex-info`.** All failures carry `:openai/error` in `ex-data`
  (see [Errors](#errors)) instead of raw HTTP client exceptions.

| wkok/openai-clojure | openai-clj |
|---|---|
| `(api/create-chat-completion {:model m :messages [...]})` | `(openai/create-response client {:model m :input "..."})` |
| `{:messages [{:role "system" :content s} ...]}` | `{:instructions s :input [...]}` |
| `(api/create-chat-completion {...} {:api-key k :organization o})` | `(openai/client {:api-key k :organization o})` once, then pass `client` |
| `{...} {:request {:timeout 60000}}` | `(openai/client {:timeout-ms 60000})` |
| `{:stream true :on-next f}` | `(openai/stream client params f)` / `(openai/stream-text client params f)` |
| `(api/create-embedding {:model m :input x})` | `(openai/create-embeddings client {:model m :input x})` |
| `(api/list-models)` / `(api/retrieve-model "id")` | `(openai/list-models client)` / `(openai/get-model client "id")` |
| `(api/upload-file {:purpose p :file f})` | `(openai/upload-file client {:purpose p :file f})` |
| `(api/retrieve-file-content "id")` | `(openai/file-content client "id")` → `byte[]` |
| `(api/create-batch {...})` / `retrieve-batch` / `cancel-batch` | `create-batch` / `get-batch` / `cancel-batch`, client-first |
| Azure: `{:impl :azure}` + env vars | `(openai/client {:base-url "https://<resource>.openai.azure.com" :azure-service-version "..."})` |

Not covered here: images, audio, moderations (as a standalone API),
assistants/threads/runs, and vector-store management are out of openai-clj's
scope - keep wkok/openai-clojure for those surfaces or drop to the Java SDK
directly.

