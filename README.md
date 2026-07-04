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
net.clojars.savya/openai-clj {:mvn/version "0.1.0"}
```

Leiningen:

```clojure
[net.clojars.savya/openai-clj "0.1.0"]
```

Tracks [`com.openai/openai-java` 4.41.0](https://github.com/openai/openai-java/releases/tag/v4.41.0).

## Usage

```clojure
(require '[openai.core :as openai])

(def client (openai/client)) ; reads OPENAI_API_KEY

(openai/create-response
 client
 {:model "gpt-5.2"
  :input "Write one sentence about Clojure maps."})
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
```

### Models And Response Lifecycle

```clojure
(openai/list-models client)
(openai/get-model client "gpt-5.2")
(openai/get-response client "resp_123")
(openai/cancel-response client "resp_123")
(openai/delete-response client "resp_123")
```

## Scope

This wraps the Responses API and models; chat completions, embeddings, images,
audio, realtime, and batches are out of scope for now.

## Running tests

```bash
clojure -M:test
```

Unit tests are no-network; `^:integration` tests, if added later, should be
skipped without `OPENAI_API_KEY`.

## License

Copyright © 2026 Savyasachi.

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
