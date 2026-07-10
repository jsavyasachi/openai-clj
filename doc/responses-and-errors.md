# Models And Response Lifecycle

```clojure
(openai/list-models client)
(openai/get-model client "gpt-5.2")
(openai/get-response client "resp_123")
(openai/list-input-items client "resp_123")
(openai/count-input-tokens client {:model "gpt-5.2" :input "Count me."})
(openai/compact client "resp_123")
(openai/cancel-response client "resp_123")
(openai/delete-response client "resp_123")
```

### Response Maps

Output messages include text/refusal content. Text content includes
`:annotations` when the SDK returns URL, file, container-file, or file-path
citations, and `:logprobs` when requested and returned. Output item variants
currently normalized with explicit types:
`:message`, `:function-call`, `:reasoning`, `:web-search-call`,
`:file-search-call`, `:code-interpreter-call`, `:image-generation-call`,
`:mcp-call`, `:mcp-list-tools`, `:mcp-approval-request`,
`:custom-tool-call`, `:local-shell-call`, `:computer-call`, and `:unknown`.

Incomplete responses include `:incomplete-details`, for example
`{:reason :max-output-tokens}`.

## Errors

All failures throw `ex-info` keyed `:openai/error` in `ex-data`:

- Request-shaping errors (bad tool spec, missing key) throw before any network
  call, with an error keyword describing the problem.
- API failures carry `{:openai/error :api-error :status <http status>
  :error-type <kw>}` where `:error-type` is one of `:bad-request`,
  `:unauthorized`, `:permission-denied`, `:not-found`,
  `:unprocessable-entity`, `:rate-limit`, `:internal-server`, or
  `:unexpected-status`. The original SDK exception is preserved as
  `(ex-cause e)`.
- Network/IO failures carry `{:openai/error :io-error}`, original exception as
  cause.

Other SDK exceptions (e.g. `OpenAIInvalidDataException`) propagate unchanged.

