# openai-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/openai-clj.svg)](https://clojars.org/net.clojars.savya/openai-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/openai-clj)](https://cljdoc.org/d/net.clojars.savya/openai-clj/CURRENT)
[![test](https://github.com/jsavyasachi/openai-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/openai-clj/actions/workflows/test.yml)

Idiomatic Clojure wrapper over the stable OpenAI API surface exposed by the
official Java SDK.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://platform.openai.com/docs/api-reference/responses"><img src="https://img.shields.io/badge/OpenAI-412991?style=flat&logo=openai&logoColor=fff" alt="OpenAI" /></a>

## Installation

deps.edn:

```clojure
net.clojars.savya/openai-clj {:mvn/version "0.9.0"}
```

Leiningen:

```clojure
[net.clojars.savya/openai-clj "0.9.0"]
```

Tracks [`com.openai/openai-java` 4.42.0](https://github.com/openai/openai-java/releases/tag/v4.42.0).

## Documentation

- [Tools](doc/tools.md)
- [Streaming](doc/streaming.md)
- [Embeddings, Files and Batches](doc/embeddings-files-batches.md)
- [Images and Audio](doc/images-and-audio.md)
- [Vector Stores](doc/vector-stores.md)
- [Fine-tuning](doc/fine-tuning.md)
- [Evals](doc/evals.md)
- [Admin](doc/admin.md)
- [Webhooks](doc/webhooks.md)
- [Skills, Videos, Containers, Uploads and Conversations](doc/additional-apis.md)
- [Azure OpenAI](doc/azure.md)
- [Responses and Errors](doc/responses-and-errors.md)
- [Migrating from wkok/openai-clojure](doc/migrating.md)

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

## Responses API

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
(def request
  {:model "gpt-5.2"
   :input "Return an answer object."
   :json-schema {:name "answer"
                 :description "One answer"
                 :strict true
                 :schema {:type "object"
                          :properties {:answer {:type "string"}}
                          :required ["answer"]}}})
```

Parse and validate the returned JSON against the same schema:

```clojure
(def response (openai/create-response client request))
(openai/parse-structured-output response (:json-schema request))
;; => {:data {"answer" "..."} :errors []}
```

Responses tools cover `:function`, `:web-search`, `:file-search`,
`:code-interpreter`, `:programmatic-tool-calling`, `:image-generation`,
`:computer`, `:local-shell`, `:shell`, `:apply-patch`, `:custom`,
`:tool-search`, and `:mcp`. Vector input accepts the matching
`:function-call-output`, `:computer-call-output`, `:local-shell-call-output`,
`:shell-call-output`, `:apply-patch-call-output`, `:custom-tool-call-output`,
`:tool-search-output`, and `:mcp-approval-response` items.

Response maps preserve all SDK output-item variants as kebab-case Clojure maps.
`openai/stream` normalizes every Responses streaming event and calls its
callback with the resulting `:type`-keyed map; `openai/stream-text` is the
text-delta convenience wrapper.

## Realtime API

`openai.realtime` provides normalized WebSocket events through callbacks or a
blocking queue:

```clojure
(require '[openai.realtime :as realtime])

(def connection
  (realtime/connect {:api-key (System/getenv "OPENAI_API_KEY")
                     :model "gpt-realtime"}))

(realtime/send! connection
                {:type :session.update
                 :session {:type :realtime
                           :instructions "Be concise."}})
(realtime/poll! connection 5000) ; normalized server event, or nil
(realtime/close! connection)
```

The namespace also exposes client-secret creation, legacy session and
transcription-session creation, translation client secrets and WebSockets, and
SIP `accept-call`, `hangup-call`, `refer-call`, and `reject-call` operations.

## Chat Completions

Prefer the Responses API for new OpenAI work. Chat Completions is exposed as
the compatibility path for OpenAI-compatible endpoints that do not support
Responses, including local LLMs and hosted compat providers.

```clojure
(openai/create-chat-completion
 client
 {:model "gpt-4o-mini"
  :messages [{:role :system :content "Be terse."}
             {:role :user :content "Write one sentence about Clojure maps."}]})
;; => {:id "chatcmpl_..."
;;     :model "gpt-4o-mini"
;;     :created 1790000000
;;     :choices [{:index 0
;;                :finish-reason :stop
;;                :message {:role :assistant
;;                          :content "Clojure maps are ..."}}]
;;     :text "Clojure maps are ..."
;;     :usage {:prompt-tokens 14 :completion-tokens 12 :total-tokens 26}}
```

Function tools use the same JSON-schema-shaped `:parameters` maps as Responses:

```clojure
(openai/create-chat-completion
 client
 {:model "gpt-4o-mini"
  :messages [{:role :user :content "Weather in Denver?"}]
  :tools [{:type :function
           :name "get_weather"
           :description "Get current weather"
           :strict true
           :parameters {:type "object"
                        :properties {:location {:type "string"}}
                        :required ["location"]}}]
  :tool-choice {:type :function :name "get_weather"}})
```

Streaming returns the concatenated content and calls the callback for each
normalized chunk:

```clojure
(openai/stream-chat-completion-text
 client
 {:model "gpt-4o-mini"
  :messages [{:role :user :content "Count to three."}]
  :stream-options {:include-usage true}}
 println)
```

## API namespaces

Service functions take an `openai.core/client` as their first argument and
accept kebab-case request maps. Realtime WebSockets take a transport config map.

```clojure
(require '[openai.images :as images]
         '[openai.audio :as audio]
         '[openai.moderations :as moderations]
         '[openai.completions :as completions]
         '[openai.vector-stores :as vector-stores]
         '[openai.uploads :as uploads]
         '[openai.containers :as containers]
         '[openai.conversations :as conversations]
         '[openai.fine-tuning :as fine-tuning]
         '[openai.evals :as evals]
         '[openai.skills :as skills]
         '[openai.videos :as videos]
         '[openai.realtime :as realtime]
         '[openai.webhooks :as webhooks]
         '[openai.admin :as admin]
         '[openai.admin.projects :as admin-projects])

(images/generate client {:model "gpt-image-1" :prompt "A Clojure logo"})
(audio/create-speech client {:model "gpt-4o-mini-tts" :voice :alloy
                             :input "Hello"})
(moderations/create client {:input "text"})
(completions/create client {:model "gpt-3.5-turbo-instruct" :prompt "Once"})
(vector-stores/create client {:name "docs" :file-ids ["file_..."]})
(uploads/create client {:filename "data.jsonl" :bytes 100
                        :mime-type "application/jsonl" :purpose :fine-tune})
(containers/create client {:name "sandbox"})
(conversations/create client {:items [{:role :user :content "Hello"}]})
(fine-tuning/create-job client {:model "gpt-4.1-mini"
                                :training-file "file_..."})
(evals/list client {:limit 20})
(skills/list client {:limit 20})
(videos/create client {:model "sora-2" :prompt "Ocean sunrise"
                       :size "1280x720" :seconds "8"})
(webhooks/unwrap webhook-client raw-body request-headers)
(admin/project-list admin-client {:limit 20})
(admin-projects/service-account-list admin-client "proj_...")
```

`openai.core` also contains Responses, Chat Completions, embeddings, files,
batches, models, and stored Chat Completions. `openai.realtime` contains
WebSocket, session, client-secret, transcription, translation, and SIP call
helpers. `openai.graders` reflects the stable grader-model service, which
exposes no operations in SDK 4.42.0.

Out of scope: other beta APIs, async clients, raw-response accessors, and
per-call `RequestOptions`.

## Running tests

```bash
clojure -M:test
```

Unit tests are no-network; `^:integration` tests, if added later, should be
skipped without `OPENAI_API_KEY`.

## License

Copyright © 2026 Savyasachi.

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
