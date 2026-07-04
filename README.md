# openai-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/openai-clj.svg)](https://clojars.org/net.clojars.savya/openai-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/openai-clj)](https://cljdoc.org/d/net.clojars.savya/openai-clj/CURRENT)
[![test](https://github.com/jsavyasachi/openai-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/openai-clj/actions/workflows/test.yml)

Idiomatic Clojure wrapper over the official OpenAI Java SDK, focused on the
Responses API.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://platform.openai.com/docs/api-reference/responses"><img src="https://img.shields.io/badge/OpenAI-412991?style=flat&logo=openai&logoColor=fff" alt="OpenAI" /></a>

## Installation

deps.edn:

```clojure
net.clojars.savya/openai-clj {:mvn/version "0.4.0"}
```

Leiningen:

```clojure
[net.clojars.savya/openai-clj "0.4.0"]
```

Tracks [`com.openai/openai-java` 4.41.0](https://github.com/openai/openai-java/releases/tag/v4.41.0).

## Usage

```clojure
(require '[openai.core :as openai])

(def client (openai/client)) ; reads OPENAI_API_KEY

(def configured-client
  (openai/client {:api-key "sk-..."
                  :organization "org_..."
                  :project "proj_..."
                  :base-url "https://api.openai.com/v1"
                  :timeout-ms 60000
                  :max-retries 2}))

(openai/create-response
 client
 {:model "gpt-5.2"
  :input "Write one sentence about Clojure maps."
  :instructions "Be precise."
  :max-output-tokens 256
  :temperature 0.2
  :top-p 1.0
  :metadata {:app "docs"}
  :store true
  :reasoning {:effort :low}})
;; => {:id "resp_..."
;;     :model "gpt-5.2"
;;     :status :completed
;;     :output [{:type :message
;;               :role :assistant
;;               :id "msg_..."
;;               :content [{:type :text :text "Clojure maps are ..."}]}]
;;     :text "Clojure maps are ..."
;;     :usage {:input-tokens 14 :output-tokens 12 :total-tokens 26}
;;     :created-at 1790000000.0}
```

Request maps support `:model`, `:input`, `:instructions`,
`:max-output-tokens`, `:max-tool-calls`, `:temperature`, `:top-p`,
`:top-logprobs`, `:metadata`, `:previous-response-id`, `:store`, `:user`,
`:reasoning`, `:tools`, `:tool-choice`, `:parallel-tool-calls`, `:background`,
`:include`, `:truncation`, `:prompt-cache-key`, `:safety-identifier`,
`:service-tier`, `:json-schema`, `:verbosity` (`:low`/`:medium`/`:high`),
`:conversation` (a conversation id string), `:stream-options`
(`{:include-obfuscation true}`), and `:moderation` (`{:model "..."}`).

Input can be a string or a vector of message items. Message content can be a
string or a vector of multimodal parts:

```clojure
{:model "gpt-5.2"
 :input [{:role :user
          :content [{:type :text :text "Summarize this image."}
                    {:type :image
                     :image-url "https://example.test/chart.png"
                     :detail :high}
                    {:type :file
                     :filename "notes.pdf"
                     :file-data "data:application/pdf;base64,..."}]}]}
```

Structured outputs use `:json-schema`:

```clojure
{:model "gpt-5.2"
 :input "Return an answer object."
 :json-schema {:name "answer"
               :description "One answer"
               :strict true
               :schema {:type "object"
                        :properties {:answer {:type "string"}}
                        :required ["answer"]}}}
```

### Function Tools

```clojure
(def weather-tool
  {:type :function
   :name "get_weather"
   :description "Get current weather for a location"
   :strict true
   :parameters {:type "object"
                :properties {:location {:type "string"}}
                :required ["location"]}})

(def first-response
  (openai/create-response
   client
   {:model "gpt-5.2"
    :input "What is the weather in Denver?"
    :tools [weather-tool]
    :tool-choice :auto}))

(def call
  (->> (:output first-response)
       (filter #(= :function-call (:type %)))
       first))

(openai/create-response
 client
 {:model "gpt-5.2"
  :previous-response-id (:id first-response)
  :input [{:type :function-call-output
           :call-id (:call-id call)
           :output {:temperature_f 72 :conditions "sunny"}}]})
```

### Built-In Tools

```clojure
{:tools [{:type :web-search
          :search-context-size :low
          :user-location {:city "Denver"
                          :country "US"
                          :region "CO"
                          :timezone "America/Denver"}
          :allowed-domains ["example.com"]}
         {:type :file-search
          :vector-store-ids ["vs_123"]
          :max-num-results 5
          :filters {:type "eq" :key "kind" :value "docs"}
          :ranking-options {:ranker "auto" :score-threshold 0.5}}
         {:type :code-interpreter}
         {:type :code-interpreter :container "cntr_123"}
         {:type :mcp
          :server-label "docs"
          :server-url "https://mcp.example.test"
          :allowed-tools ["search"]
          :require-approval :never
          :headers {"X-Trace" "1"}}]}
```

Tool choice accepts `:auto`, `:required`, `:none`, or
`{:type :function :name "get_weather"}`.

### Streaming

```clojure
(openai/stream
 client
 {:model "gpt-5.2" :input "Count to three."}
 prn)
;; prints normalized event maps and returns the concatenated output text

(openai/stream-text
 client
 {:model "gpt-5.2" :input "Count to three."}
 print)
;; prints text deltas and returns the concatenated output text

(openai/retrieve-streaming client "resp_123" prn)
;; resumes streaming an existing background response
```

### Embeddings

```clojure
(openai/create-embeddings
 client
 {:model "text-embedding-3-small"
  :input ["first text" "second text"]
  :dimensions 256})
;; => {:model "text-embedding-3-small"
;;     :embeddings [[0.01 -0.02 ...] [0.03 0.04 ...]]
;;     :usage {:prompt-tokens 8 :total-tokens 8}}
```

### Azure OpenAI

```clojure
(openai/client {:api-key key
                :base-url "https://my-resource.openai.azure.com"
                :azure-service-version "2024-10-21"})
```

### Models And Response Lifecycle

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

## Scope

In scope: Responses API, structured outputs, multimodal input parts, response
streaming, response lifecycle subservices, response compaction, token counting,
built-in Responses tools, MCP tools, client options (including Azure OpenAI
endpoints), embeddings, and models.

Out of scope: chat completions, embeddings, images API, audio, realtime, and
batches.

## Running tests

```bash
clojure -M:test
```

Unit tests are no-network; `^:integration` tests, if added later, should be
skipped without `OPENAI_API_KEY`.

## License

Copyright © 2026 Savyasachi.

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
