(ns openai.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.core :as openai])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models ResponseFormatJsonSchema
                              ResponseFormatJsonSchema$JsonSchema
                              ResponseFormatJsonSchema$JsonSchema$Schema)
           (com.openai.models.chat.completions ChatCompletion
                                               ChatCompletion$Choice
                                               ChatCompletion$Choice$FinishReason
                                               ChatCompletion$ServiceTier
                                               ChatCompletionChunk
                                               ChatCompletionChunk$Choice
                                               ChatCompletionChunk$Choice$Delta
                                               ChatCompletionChunk$Choice$Delta$Role
                                               ChatCompletionChunk$Choice$Delta$ToolCall
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Function
                                               ChatCompletionChunk$Choice$Delta$ToolCall$Type
                                               ChatCompletionChunk$Choice$FinishReason
                                               ChatCompletionContentPart
                                               ChatCompletionCreateParams
                                               ChatCompletionCreateParams$ServiceTier
                                               ChatCompletionMessage
                                               ChatCompletionMessageFunctionToolCall
                                               ChatCompletionMessageFunctionToolCall$Function
                                               ChatCompletionMessageToolCall
                                               ChatCompletionMessageParam
                                               ChatCompletionStoreMessage)
           (com.openai.models.completions CompletionUsage)
           (com.openai.models.models Model)
           (com.openai.models.responses ResponseCreateParams
                                        ResponseCreateParams$ToolChoice
                                        Response$IncompleteDetails
                                        Response$IncompleteDetails$Reason
                                        ResponseCompletedEvent
                                        ResponseCreatedEvent
                                        ResponseErrorEvent
                                        ResponseFailedEvent
                                        ResponseFunctionToolCall
                                        ResponseFunctionCallArgumentsDeltaEvent
                                        ResponseFunctionCallArgumentsDoneEvent
                                        ResponseIncompleteEvent
                                        ResponseInProgressEvent
                                        ResponseInputContent
                                        ResponseInputItem
                                        ResponseOutputItemAddedEvent
                                        ResponseOutputItemDoneEvent
                                        ResponseOutputItem
                                        ResponseOutputItem$McpCall
                                        ResponseOutputItem$McpCall$Status
                                        ResponseOutputItem$McpListTools
                                        ResponseOutputItem$McpApprovalRequest
                                        ResponseOutputItem$ImageGenerationCall
                                        ResponseOutputItem$ImageGenerationCall$Status
                                        ResponseOutputItem$LocalShellCall
                                        ResponseOutputItem$LocalShellCall$Action
                                        ResponseOutputItem$LocalShellCall$Action$Env
                                        ResponseOutputItem$LocalShellCall$Status
                                        ResponseOutputMessage
                                        ResponseOutputMessage$Content
                                        ResponseOutputMessage$Status
                                        ResponseOutputRefusal
                                        ResponseOutputText
                                        ResponseOutputText$Annotation
                                        ResponseOutputText$Annotation$UrlCitation
                                        ResponseOutputText$Logprob
                                        ResponseOutputText$Logprob$TopLogprob
                                        ResponseReasoningItem
                                        ResponseReasoningItem$Status
                                        ResponseReasoningItem$Summary
                                        ResponseCustomToolCall
                                        ResponseComputerToolCall
                                        ResponseComputerToolCall$Status
                                        ResponseComputerToolCall$Type
                                        ResponseReasoningTextDeltaEvent
                                        ResponseReasoningTextDoneEvent
                                        ResponseRefusalDeltaEvent
                                        ResponseRefusalDoneEvent
                                        ResponseStatus
                                        ResponseStreamEvent
                                        ResponseTextDeltaEvent
                                        ResponseTextDoneEvent
                                        ResponseUsage
                                        ResponseUsage$InputTokensDetails
                                        ResponseUsage$OutputTokensDetails
                                        ToolChoiceOptions)))

(defn- params ^ResponseCreateParams [m]
  (#'openai/->params m))

(defn- response->map [r]
  (#'openai/response->map r))

(defn- event->map [e]
  (#'openai/event->map e))

(defn- model->map [m]
  (#'openai/model->map m))

(defn- input-token-count-params [m]
  (#'openai/->input-token-count-params m))

(defn- chat-params ^ChatCompletionCreateParams [m]
  (#'openai/->chat-params m))

(defn- chat-completion->map [x]
  (#'openai/chat-completion->map x))

(defn- chat-chunk->map [x]
  (#'openai/chat-chunk->map x))

(defn- model-delete-params [id]
  (#'openai/->model-delete-params id))

(defn- chat-completion-update-params [id opts]
  (#'openai/->chat-completion-update-params id opts))

(defn- chat-completion-list-params [opts]
  (#'openai/->chat-completion-list-params opts))

(defn- chat-completion-message-list-params [id opts]
  (#'openai/->chat-completion-message-list-params id opts))

(defn- stored-chat-message->map [x]
  (#'openai/stored-chat-message->map x))

(defn- deleted-model->map [x]
  (#'openai/deleted-model->map x))

(defn- deleted-chat-completion->map [x]
  (#'openai/deleted-chat-completion->map x))

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- ex-data-for [f]
  (try
    (f)
    nil
    (catch clojure.lang.ExceptionInfo e
      (ex-data e))))

(deftest builds-client-from-explicit-config
  (let [c (openai/client {:api-key "sk-test"
                          :organization "org-test"
                          :project "proj-test"
                          :base-url "https://example.test/v1"
                          :timeout-ms 1000
                          :max-retries 1})]
    (is (instance? OpenAIClient c))
    (.close ^OpenAIClient c)))

(deftest translates-string-input
  (let [p (params {:model "gpt-5.2" :input "plain string"})]
    (is (= "gpt-5.2" (.asString (opt (.model p)))))
    (is (= "plain string" (.asText (opt (.input p)))))))

(deftest translates-message-vector-input
  (let [p (params {:model "gpt-5.2"
                   :input [{:role :system :content "sys"}
                           {:role :developer :content "dev"}
                           {:role :user :content "hi"}
                           {:role :assistant :content "there"}]})
        input (opt (.input p))
        items (.asResponse input)]
    (is (= ["system" "developer" "user" "assistant"]
           (mapv #(-> ^ResponseInputItem %
                      .asEasyInputMessage
                      .role
                      .asString)
                 items)))
    (is (= ["sys" "dev" "hi" "there"]
           (mapv #(-> ^ResponseInputItem %
                      .asEasyInputMessage
                      .content
                      .asTextInput)
                 items)))))

(deftest translates-scalar-options
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :instructions "follow these"
                   :max-output-tokens 512
                   :max-tool-calls 7
                   :temperature 0.7
                   :top-p 0.9
                   :top-logprobs 3
                   :background true
                   :include [:web-search-call.action.sources
                             :message.output-text.logprobs]
                   :truncation :auto
                   :prompt-cache-key "cache-key"
                   :safety-identifier "safe-user"
                   :service-tier :priority
                   :previous-response-id "resp_123"
                   :store false
                   :user "end-user-id"
                   :reasoning {:effort :medium}})]
    (is (= "follow these" (opt (.instructions p))))
    (is (= 512 (opt (.maxOutputTokens p))))
    (is (= 7 (opt (.maxToolCalls p))))
    (is (= 0.7 (opt (.temperature p))))
    (is (= 0.9 (opt (.topP p))))
    (is (= 3 (opt (.topLogprobs p))))
    (is (true? (opt (.background p))))
    (is (= ["web_search_call.action.sources" "message.output_text.logprobs"]
           (mapv #(.asString %) (opt (.include p)))))
    (is (= "auto" (.asString (opt (.truncation p)))))
    (is (= "cache-key" (opt (.promptCacheKey p))))
    (is (= "safe-user" (opt (.safetyIdentifier p))))
    (is (= "priority" (.asString (opt (.serviceTier p)))))
    (is (= "resp_123" (opt (.previousResponseId p))))
    (is (false? (opt (.store p))))
    (is (= "end-user-id" (opt (.user p))))
    (is (= "medium" (-> p .reasoning opt .effort opt .asString)))))

(deftest translates-metadata
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :metadata {:foo "bar" "baz" "quux"}})
        props (._additionalProperties (opt (.metadata p)))]
    (is (= "bar" (.asStringOrThrow (get props "foo"))))
    (is (= "quux" (.asStringOrThrow (get props "baz"))))))

(deftest rejects-missing-required-keys
  (testing "missing model"
    (is (= {:openai/error :missing-key :key :model}
           (ex-data-for #(params {:input "hi"})))))
  (testing "missing input"
    (is (= {:openai/error :missing-key :key :input}
           (ex-data-for #(params {:model "gpt-5.2"})))))
  (testing "missing role"
    (is (= {:openai/error :missing-key :key :role}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:content "hi"}]})))))
  (testing "missing content"
    (is (= {:openai/error :missing-key :key :content}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:role :user}]}))))))

(deftest ignores-unknown-keys
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :unknown "ignored"})]
    (is (= "gpt-5.2" (.asString (opt (.model p)))))
    (is (= "hi" (.asText (opt (.input p)))))))

(deftest translates-tools
  (testing "function tool"
    (let [p (params {:model "gpt-5.2"
                     :input "hi"
                     :tools [{:type :function
                              :name "get_weather"
                              :description "Get the weather"
                              :strict true
                              :parameters {:type "object"
                                           :properties {:location {:type "string"}}
                                           :required ["location"]}}]})
          t (first (opt (.tools p)))
          f (.asFunction t)
          props (._additionalProperties (opt (.parameters f)))]
      (is (.isFunction t))
      (is (= "get_weather" (.name f)))
      (is (= "Get the weather" (opt (.description f))))
      (is (true? (opt (.strict f))))
      (is (= "object" (.asStringOrThrow (get props "type"))))
      (is (= ["location"] (.convert (get props "required") java.util.List)))))
  (testing "web search tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :web-search
                                                  :search-context-size :high
                                                  :user-location {:city "Denver"
                                                                  :country "US"
                                                                  :region "CO"
                                                                  :timezone "America/Denver"}
                                                  :allowed-domains ["example.com"]}]}))))]
      (is (.isWebSearch t))
      (is (= "high" (.asString (opt (.searchContextSize (.asWebSearch t))))))
      (is (= "Denver" (opt (.city (opt (.userLocation (.asWebSearch t)))))))
      (is (= ["example.com"]
             (vec (opt (.allowedDomains (opt (.filters (.asWebSearch t))))))))))
  (testing "file search tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :max-num-results 5
                                                  :filters {:type "eq"
                                                            :key "kind"
                                                            :value "docs"}
                                                  :ranking-options {:ranker "auto"
                                                                    :score-threshold 0.5}}]}))))]
      (is (.isFileSearch t))
      (is (= ["vs_1"] (vec (.vectorStoreIds (.asFileSearch t)))))
      (is (= 5 (opt (.maxNumResults (.asFileSearch t)))))
      (is (= "kind" (.key (.asComparisonFilter (opt (.filters (.asFileSearch t)))))))
      (is (= "auto" (.asString (opt (.ranker (opt (.rankingOptions (.asFileSearch t))))))))
      (is (= 0.5 (opt (.scoreThreshold (opt (.rankingOptions (.asFileSearch t)))))))))
  (testing "mcp tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :mcp
                                                  :server-label "docs"
                                                  :server-url "https://mcp.example.test"
                                                  :allowed-tools ["search"]
                                                  :require-approval :never
                                                  :headers {"X-Test" "1"}}]}))))
          m (.asMcp t)]
      (is (.isMcp t))
      (is (= "docs" (.serverLabel m)))
      (is (= "https://mcp.example.test" (opt (.serverUrl m))))
      (is (= ["search"] (vec (.asMcp (opt (.allowedTools m))))))
      (is (= "never" (.asString (.asMcpToolApprovalSetting (opt (.requireApproval m))))))
      (is (= "1" (.asStringOrThrow (get (._additionalProperties (opt (.headers m))) "X-Test"))))))
  (testing "code interpreter defaults to auto container"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :code-interpreter}]}))))]
      (is (.isCodeInterpreter t))
      (is (.isCodeInterpreterToolAuto (.container (.asCodeInterpreter t))))))
  (testing "code interpreter accepts container id"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :code-interpreter
                                                  :container "cntr_123"}]}))))]
      (is (= "cntr_123" (.asString (.container (.asCodeInterpreter t)))))))
  (testing "unknown tool type"
    (is (= {:openai/error :unknown-tool-type :type :bogus}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :tools [{:type :bogus}]}))))))

(deftest translates-tool-choice
  (doseq [[choice expected] [[:auto ToolChoiceOptions/AUTO]
                             [:required ToolChoiceOptions/REQUIRED]
                             [:none ToolChoiceOptions/NONE]]]
    (let [tc (opt (.toolChoice (params {:model "gpt-5.2"
                                        :input "hi"
                                        :tool-choice choice})))]
      (is (.isOptions tc))
      (is (= expected (.asOptions tc)))))
  (let [^ResponseCreateParams$ToolChoice tc
        (opt (.toolChoice (params {:model "gpt-5.2"
                                   :input "hi"
                                   :tool-choice {:type :function
                                                 :name "get_weather"}})))]
    (is (.isFunction tc))
    (is (= "get_weather" (.name (.asFunction tc))))))

(deftest translates-parallel-tool-calls
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :parallel-tool-calls false})]
    (is (false? (opt (.parallelToolCalls p))))))

(deftest translates-json-schema-output-format
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :json-schema {:name "answer"
                                 :description "Structured answer"
                                 :strict true
                                 :schema {:type "object"
                                          :properties {:answer {:type "string"}}
                                          :required ["answer"]}}})
        cfg (-> p .text opt .format opt .asJsonSchema)
        props (._additionalProperties (.schema cfg))
        answer-props (-> (get props "properties")
                         (.convert java.util.Map)
                         (get "answer"))]
    (is (= "answer" (.name cfg)))
    (is (= "Structured answer" (opt (.description cfg))))
    (is (true? (opt (.strict cfg))))
    (is (= "object" (.asStringOrThrow (get props "type"))))
    (is (= "string" (get answer-props "type"))))
  (testing "missing name"
    (is (= {:openai/error :missing-key :key :name}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :json-schema {:schema {:type "object"}}})))))
  (testing "missing schema"
    (is (= {:openai/error :missing-key :key :schema}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input "hi"
                                  :json-schema {:name "answer"}}))))))

(deftest translates-input-token-count-params
  (let [p (input-token-count-params {:model "gpt-5.2"
                                     :input [{:role :user :content "hi"}]
                                     :instructions "count this"
                                     :previous-response-id "resp_123"
                                     :parallel-tool-calls false
                                     :reasoning {:effort :low}
                                     :tools [{:type :web-search}]
                                     :tool-choice {:type :function
                                                   :name "get_weather"}
                                     :truncation :auto
                                     :max-output-tokens 99
                                     :metadata {:ignored true}})
        item (first (.asResponseInputItems (opt (.input p))))]
    (is (= "gpt-5.2" (opt (.model p))))
    (is (= "count this" (opt (.instructions p))))
    (is (= "resp_123" (opt (.previousResponseId p))))
    (is (false? (opt (.parallelToolCalls p))))
    (is (= "low" (-> p .reasoning opt .effort opt .asString)))
    (is (.isWebSearch (first (opt (.tools p)))))
    (is (= "get_weather" (.name (.asFunction (opt (.toolChoice p))))))
    (is (= "auto" (.asString (opt (.truncation p)))))
    (is (.isEasyInputMessage item))))

(deftest translates-function-call-output-input
  (testing "string output"
    (let [p (params {:model "gpt-5.2"
                     :input [{:type :function-call-output
                              :call-id "call_123"
                              :output "sunny"}]})
          item (first (.asResponse (opt (.input p))))
          fco (.asFunctionCallOutput item)]
      (is (.isFunctionCallOutput item))
      (is (= "call_123" (.callId fco)))
      (is (= "sunny" (.asString (.output fco))))))
  (testing "map output is encoded as JSON"
    (let [p (params {:model "gpt-5.2"
                     :input [{:type :function-call-output
                              :call-id "call_123"
                              :output {:forecast "sunny"}}]})
          fco (.asFunctionCallOutput (first (.asResponse (opt (.input p)))))]
      (is (= "{\"forecast\":\"sunny\"}" (.asString (.output fco)))))))

(defn- message-content-list [p]
  (-> ^ResponseCreateParams p
      .input
      opt
      .asResponse
      first
      .asEasyInputMessage
      .content
      .asResponseInputMessageContentList))

(deftest translates-multimodal-message-content
  (testing "mixed text and image url"
    (let [parts (message-content-list
                 (params {:model "gpt-5.2"
                          :input [{:role :user
                                   :content [{:type :text :text "look"}
                                             {:type :image
                                              :image-url "https://example.test/cat.png"
                                              :detail :high}]}]}))
          text-part (.asInputText ^ResponseInputContent (first parts))
          image-part (.asInputImage ^ResponseInputContent (second parts))]
      (is (= "look" (.text text-part)))
      (is (= "https://example.test/cat.png" (opt (.imageUrl image-part))))
      (is (= "high" (.asString (.detail image-part))))))
  (testing "image file id"
    (let [image-part (-> (message-content-list
                          (params {:model "gpt-5.2"
                                   :input [{:role :user
                                            :content [{:type :image
                                                       :file-id "file_123"
                                                       :detail :low}]}]}))
                         first
                         .asInputImage)]
      (is (= "file_123" (opt (.fileId image-part))))
      (is (= "low" (.asString (.detail image-part))))))
  (testing "file part"
    (let [file-part (-> (message-content-list
                         (params {:model "gpt-5.2"
                                  :input [{:role :user
                                           :content [{:type :file
                                                      :filename "paper.pdf"
                                                      :file-data "data:application/pdf;base64,AAAA"}]}]}))
                        first
                        .asInputFile)]
      (is (= "paper.pdf" (opt (.filename file-part))))
      (is (= "data:application/pdf;base64,AAAA" (opt (.fileData file-part))))))
  (testing "unknown part type"
    (is (= {:openai/error :unknown-content-type :type :audio}
           (ex-data-for #(params {:model "gpt-5.2"
                                  :input [{:role :user
                                           :content [{:type :audio
                                                      :text "hi"}]}]}))))))

(defn- text-content
  ([s] (text-content s []))
  ([s annotations]
  (ResponseOutputMessage$Content/ofOutputText
   (-> (ResponseOutputText/builder)
       (.text s)
       (.annotations annotations)
       (.build)))))

(defn- refusal-content [s]
  (ResponseOutputMessage$Content/ofRefusal
   (-> (ResponseOutputRefusal/builder)
       (.refusal s)
       (.build))))

(defn- message-item []
  (ResponseOutputItem/ofMessage
   (-> (ResponseOutputMessage/builder)
       (.id "msg_1")
       (.status ResponseOutputMessage$Status/COMPLETED)
       (.content [(text-content "Hello, ")
                  (text-content "world")
                  (refusal-content "nope")])
       (.build))))

(defn- function-call-item [args]
  (ResponseOutputItem/ofFunctionCall
   (-> (ResponseFunctionToolCall/builder)
       (.id "fc_1")
       (.callId "call_123")
       (.name "get_weather")
       (.arguments args)
       (.build))))

(defn- unknown-item []
  (ResponseOutputItem/ofImageGenerationCall
   (-> (ResponseOutputItem$ImageGenerationCall/builder)
       (.id "img_1")
       (.result "base64")
       (.status (ResponseOutputItem$ImageGenerationCall$Status/of "completed"))
       (.build))))

(defn- url-annotation []
  (ResponseOutputText$Annotation/ofUrlCitation
   (-> (ResponseOutputText$Annotation$UrlCitation/builder)
       (.url "https://example.test")
       (.title "Example")
       (.startIndex 0)
       (.endIndex 5)
       (.build))))

(defn- logprob []
  (-> (ResponseOutputText$Logprob/builder)
      (.token "Hello")
      (.bytes [72 101 108 108 111])
      (.logprob -0.1)
      (.topLogprobs [(-> (ResponseOutputText$Logprob$TopLogprob/builder)
                         (.token "Hi")
                         (.bytes [72 105])
                         (.logprob -0.5)
                         (.build))])
      (.build)))

(defn- reasoning-item []
  (ResponseOutputItem/ofReasoning
   (-> (ResponseReasoningItem/builder)
       (.id "rs_1")
       (.status ResponseReasoningItem$Status/COMPLETED)
       (.summary [(-> (ResponseReasoningItem$Summary/builder)
                      (.text "short thought")
                      (.build))])
       (.build))))

(defn- mcp-call-item []
  (ResponseOutputItem/ofMcpCall
   (-> (ResponseOutputItem$McpCall/builder)
       (.id "mcp_1")
       (.name "search")
       (.serverLabel "docs")
       (.arguments "{\"q\":\"sdk\"}")
       (.output "result")
       (.status ResponseOutputItem$McpCall$Status/COMPLETED)
       (.build))))

(defn- custom-tool-call-item []
  (ResponseOutputItem/ofCustomToolCall
   (-> (ResponseCustomToolCall/builder)
       (.id "ctc_1")
       (.callId "call_custom")
       (.name "lint")
       (.input "src")
       (.build))))

(defn- local-shell-call-item []
  (ResponseOutputItem/ofLocalShellCall
   (-> (ResponseOutputItem$LocalShellCall/builder)
       (.id "shell_1")
       (.action (-> (ResponseOutputItem$LocalShellCall$Action/builder)
                    (.command ["pwd"])
                    (.env (-> (ResponseOutputItem$LocalShellCall$Action$Env/builder)
                              (.build)))
                    (.build)))
       (.callId "call_shell")
       (.status ResponseOutputItem$LocalShellCall$Status/COMPLETED)
       (.build))))

(defn- computer-call-item []
  (ResponseOutputItem/ofComputerCall
   (-> (ResponseComputerToolCall/builder)
       (.id "comp_1")
       (.callId "call_comp")
       (.pendingSafetyChecks [])
       (.status ResponseComputerToolCall$Status/COMPLETED)
       (.type ResponseComputerToolCall$Type/COMPUTER_CALL)
       (.build))))

(defn- response [items]
  (-> (com.openai.models.responses.Response/builder)
      (.id "resp_123")
      (.model "gpt-5.2")
      (.createdAt 1234.5)
      (.background (java.util.Optional/empty))
      (.completedAt (java.util.Optional/empty))
      (.conversation (java.util.Optional/empty))
      (.error (java.util.Optional/empty))
      (.incompleteDetails (java.util.Optional/empty))
      (.instructions (java.util.Optional/empty))
      (.maxOutputTokens (java.util.Optional/empty))
      (.metadata (java.util.Optional/empty))
      (.parallelToolCalls false)
      (.previousResponseId (java.util.Optional/empty))
      (.reasoning (java.util.Optional/empty))
      (.status ResponseStatus/COMPLETED)
      (.temperature (java.util.Optional/empty))
      (.toolChoice ToolChoiceOptions/AUTO)
      (.tools [])
      (.topP (java.util.Optional/empty))
      (.truncation (java.util.Optional/empty))
      (.output items)
      (.usage (-> (ResponseUsage/builder)
                  (.inputTokens 10)
                  (.inputTokensDetails (-> (ResponseUsage$InputTokensDetails/builder)
                                           (.cachedTokens 0)
                                           (.build)))
                  (.outputTokens 20)
                  (.outputTokensDetails (-> (ResponseUsage$OutputTokensDetails/builder)
                                            (.reasoningTokens 0)
                                            (.build)))
                  (.totalTokens 30)
                  (.build)))
      (.build)))

(defn- incomplete-response [items]
  (-> (response items)
      .toBuilder
      (.status ResponseStatus/INCOMPLETE)
      (.incompleteDetails (-> (Response$IncompleteDetails/builder)
                              (.reason Response$IncompleteDetails$Reason/MAX_OUTPUT_TOKENS)
                              (.build)))
      (.build)))

(deftest maps-response-to-clojure
  (let [m (response->map (response [(message-item)
                                    (function-call-item "{\"location\":\"Denver\"}")
                                    (unknown-item)
                                    (reasoning-item)
                                    (mcp-call-item)
                                    (custom-tool-call-item)
                                    (local-shell-call-item)
                                    (computer-call-item)]))]
    (is (= "resp_123" (:id m)))
    (is (= "gpt-5.2" (:model m)))
    (is (= :completed (:status m)))
    (is (= 1234.5 (:created-at m)))
    (is (= {:input-tokens 10 :output-tokens 20 :total-tokens 30}
           (:usage m)))
    (is (= "Hello, world" (:text m)))
    (is (= [{:type :message
             :role :assistant
             :id "msg_1"
             :content [{:type :text :text "Hello, "}
                       {:type :text :text "world"}
                       {:type :refusal :refusal "nope"}]}
            {:type :function-call
             :name "get_weather"
             :call-id "call_123"
             :id "fc_1"
             :arguments {:location "Denver"}}
            {:type :image-generation-call
             :id "img_1"
             :status :completed
             :result "base64"}
            {:type :reasoning
             :id "rs_1"
             :status :completed
             :summary ["short thought"]}
            {:type :mcp-call
             :id "mcp_1"
             :status :completed
             :name "search"
             :server-label "docs"
             :arguments "{\"q\":\"sdk\"}"
             :output "result"}
            {:type :custom-tool-call
             :id "ctc_1"
             :name "lint"
             :input "src"
             :call-id "call_custom"}
            {:type :local-shell-call
             :id "shell_1"
             :status :completed
             :call-id "call_shell"}
            {:type :computer-call
             :id "comp_1"
             :status :completed}]
           (:output m)))))

(deftest maps-output-text-annotations
  (let [m (response->map
           (response [(ResponseOutputItem/ofMessage
                       (-> (ResponseOutputMessage/builder)
                           (.id "msg_1")
                           (.status ResponseOutputMessage$Status/COMPLETED)
                           (.content [(text-content "Hello" [(url-annotation)])])
                           (.build)))]))]
    (is (= [{:type :url-citation
             :url "https://example.test"
             :title "Example"
             :start-index 0
             :end-index 5}]
           (-> m :output first :content first :annotations)))))

(deftest maps-output-text-logprobs
  (let [content (ResponseOutputMessage$Content/ofOutputText
                 (-> (ResponseOutputText/builder)
                     (.text "Hello")
                     (.annotations [])
                     (.logprobs [(logprob)])
                     (.build)))
        item (ResponseOutputItem/ofMessage
              (-> (ResponseOutputMessage/builder)
                  (.id "msg_1")
                  (.status ResponseOutputMessage$Status/COMPLETED)
                  (.content [content])
                  (.build)))
        m (response->map (response [item]))]
    (is (= [{:token "Hello"
             :bytes [72 101 108 108 111]
             :logprob -0.1
             :top-logprobs [{:token "Hi"
                             :bytes [72 105]
                             :logprob -0.5}]}]
           (-> m :output first :content first :logprobs)))))

(deftest maps-incomplete-details
  (let [m (response->map (incomplete-response []))]
    (is (= {:reason :max-output-tokens} (:incomplete-details m)))))

(deftest response-map-keeps-garbage-arguments-raw
  (let [m (response->map (response [(function-call-item "{not json}")]))]
    (is (= "{not json}" (-> m :output first :arguments)))
    (is (= "" (:text m)))))

(deftest maps-model-to-clojure
  (let [m (model->map (-> (Model/builder)
                          (.id "gpt-5.2")
                          (.created 1790000000)
                          (.ownedBy "openai")
                          (.build)))]
    (is (= {:id "gpt-5.2"
            :created 1790000000
            :owned-by "openai"}
           m))))

(deftest maps-stream-text-events-to-clojure
  (is (= {:type :output-text-delta
          :delta "Hel"
          :item-id "msg_1"
          :output-index 0}
         (event->map
          (ResponseStreamEvent/ofOutputTextDelta
           (-> (ResponseTextDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "Hel")
               (.itemId "msg_1")
               (.logprobs [])
               (.outputIndex 0)
               (.sequenceNumber 1)
               (.build))))))
  (is (= {:type :output-text-done
          :text "Hello"
          :item-id "msg_1"
          :output-index 0}
         (event->map
          (ResponseStreamEvent/ofOutputTextDone
           (-> (ResponseTextDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "msg_1")
               (.logprobs [])
               (.outputIndex 0)
               (.sequenceNumber 2)
               (.text "Hello")
               (.build)))))))

(deftest maps-stream-function-call-events-to-clojure
  (is (= {:type :function-call-arguments-delta
          :delta "{\""
          :item-id "fc_1"}
         (event->map
          (ResponseStreamEvent/ofFunctionCallArgumentsDelta
           (-> (ResponseFunctionCallArgumentsDeltaEvent/builder)
               (.delta "{\"")
               (.itemId "fc_1")
               (.outputIndex 1)
               (.sequenceNumber 3)
               (.build))))))
  (is (= {:type :function-call-arguments-done
          :arguments "{\"location\":\"Denver\"}"
          :item-id "fc_1"}
         (event->map
          (ResponseStreamEvent/ofFunctionCallArgumentsDone
           (-> (ResponseFunctionCallArgumentsDoneEvent/builder)
               (.arguments "{\"location\":\"Denver\"}")
               (.itemId "fc_1")
               (.name "get_weather")
               (.outputIndex 1)
               (.sequenceNumber 4)
               (.build)))))))

(deftest maps-stream-reasoning-and-refusal-events-to-clojure
  (is (= {:type :reasoning-text-delta :delta "think"}
         (event->map
          (ResponseStreamEvent/ofReasoningTextDelta
           (-> (ResponseReasoningTextDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "think")
               (.itemId "rs_1")
               (.outputIndex 0)
               (.sequenceNumber 5)
               (.build))))))
  (is (= {:type :reasoning-text-done :text "thought"}
         (event->map
          (ResponseStreamEvent/ofReasoningTextDone
           (-> (ResponseReasoningTextDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "rs_1")
               (.outputIndex 0)
               (.sequenceNumber 6)
               (.text "thought")
               (.build))))))
  (is (= {:type :refusal-delta :delta "no"}
         (event->map
          (ResponseStreamEvent/ofRefusalDelta
           (-> (ResponseRefusalDeltaEvent/builder)
               (.contentIndex 0)
               (.delta "no")
               (.itemId "msg_1")
               (.outputIndex 0)
               (.sequenceNumber 7)
               (.build))))))
  (is (= {:type :refusal-done :refusal "nope"}
         (event->map
          (ResponseStreamEvent/ofRefusalDone
           (-> (ResponseRefusalDoneEvent/builder)
               (.contentIndex 0)
               (.itemId "msg_1")
               (.outputIndex 0)
               (.refusal "nope")
               (.sequenceNumber 8)
               (.build)))))))

(deftest maps-stream-output-item-events-to-clojure
  (is (= {:type :output-item-added
          :item {:type :function-call
                 :name "get_weather"
                 :call-id "call_123"
                 :id "fc_1"
                 :arguments {:location "Denver"}}
          :output-index 1}
         (event->map
          (ResponseStreamEvent/ofOutputItemAdded
           (-> (ResponseOutputItemAddedEvent/builder)
               (.item (function-call-item "{\"location\":\"Denver\"}"))
               (.outputIndex 1)
               (.sequenceNumber 9)
               (.build))))))
  (is (= {:type :output-item-done
          :item {:type :image-generation-call
                 :id "img_1"
                 :status :completed
                 :result "base64"}
          :output-index 2}
         (event->map
          (ResponseStreamEvent/ofOutputItemDone
           (-> (ResponseOutputItemDoneEvent/builder)
               (.item (unknown-item))
               (.outputIndex 2)
               (.sequenceNumber 10)
               (.build)))))))

(deftest maps-stream-lifecycle-events-to-clojure
  (is (= {:type :created}
         (event->map
          (ResponseStreamEvent/ofCreated
           (-> (ResponseCreatedEvent/builder)
               (.response (response []))
               (.sequenceNumber 11)
               (.build))))))
  (is (= {:type :in-progress}
         (event->map
          (ResponseStreamEvent/ofInProgress
           (-> (ResponseInProgressEvent/builder)
               (.response (response []))
               (.sequenceNumber 12)
               (.build))))))
  (is (= :completed
         (:type
          (event->map
           (ResponseStreamEvent/ofCompleted
            (-> (ResponseCompletedEvent/builder)
                (.response (response [(message-item)]))
                (.sequenceNumber 13)
                (.build)))))))
  (is (= "Hello, world"
         (-> (event->map
              (ResponseStreamEvent/ofCompleted
               (-> (ResponseCompletedEvent/builder)
                   (.response (response [(message-item)]))
                   (.sequenceNumber 13)
                   (.build))))
             :response
             :text)))
  (is (= :incomplete
         (:type
          (event->map
           (ResponseStreamEvent/ofIncomplete
            (-> (ResponseIncompleteEvent/builder)
                (.response (response []))
                (.sequenceNumber 14)
                (.build)))))))
  (is (= :failed
         (:type
          (event->map
           (ResponseStreamEvent/ofFailed
            (-> (ResponseFailedEvent/builder)
                (.response (response []))
                (.sequenceNumber 15)
                (.build))))))))

(deftest maps-stream-error-and-other-events-to-clojure
  (is (= {:type :error
          :message "bad request"
          :code "invalid_request"}
         (event->map
          (ResponseStreamEvent/ofError
           (-> (ResponseErrorEvent/builder)
               (.code "invalid_request")
               (.message "bad request")
               (.param (java.util.Optional/empty))
               (.sequenceNumber 16)
               (.build))))))
  (is (= {:type :other}
         (event->map
          (ResponseStreamEvent/ofQueued
           (-> (com.openai.models.responses.ResponseQueuedEvent/builder)
               (.response (response []))
               (.sequenceNumber 17)
               (.build)))))))

(def throw-normalized! #'openai/throw-normalized!)

(defn- rate-limit-ex []
  (let [ctor (first (.getConstructors com.openai.errors.RateLimitException))
        err (-> (com.openai.models.ErrorObject/builder)
                (.code "rate_limited")
                (.message "too fast")
                (.param (java.util.Optional/empty))
                (.type "rate_limit_error")
                (.build))]
    (.newInstance ctor
                  (object-array [(.build (com.openai.core.http.Headers/builder))
                                 (com.openai.core.JsonField/of err)
                                 nil nil]))))

(deftest api-error-normalization
  (testing "service exceptions become ex-info with status, error-type, cause"
    (let [orig (rate-limit-ex)
          ex (try (throw-normalized! orig) (catch clojure.lang.ExceptionInfo e e))]
      (is (= :api-error (:openai/error (ex-data ex))))
      (is (= 429 (:status (ex-data ex))))
      (is (= :rate-limit (:error-type (ex-data ex))))
      (is (identical? orig (ex-cause ex)))))
  (testing "io exceptions become :io-error ex-info"
    (let [orig (com.openai.errors.OpenAIIoException. "boom")
          ex (try (throw-normalized! orig) (catch clojure.lang.ExceptionInfo e e))]
      (is (= :io-error (:openai/error (ex-data ex))))
      (is (identical? orig (ex-cause ex)))))
  (testing "other OpenAI exceptions pass through unchanged"
    (let [orig (com.openai.errors.OpenAIInvalidDataException. "bad" nil)
          ex (try (throw-normalized! orig) (catch Throwable e e))]
      (is (identical? orig ex)))))

(deftest translates-conversation-stream-options-moderation-verbosity
  (let [p (params {:model "gpt-5.2"
                   :input "hi"
                   :conversation "conv_123"
                   :stream-options {:include-obfuscation true}
                   :moderation {:model "omni-moderation-latest"}
                   :verbosity :low})]
    (is (= "conv_123" (-> p .conversation opt .asId)))
    (is (true? (-> p .streamOptions opt .includeObfuscation opt)))
    (is (= "omni-moderation-latest" (-> p .moderation opt .model)))
    (is (= "low" (-> p .text opt .verbosity opt str)))))

(def ->embedding-params #'openai/->embedding-params)
(def embedding-response->map #'openai/embedding-response->map)

(deftest translates-embedding-params
  (let [^com.openai.models.embeddings.EmbeddingCreateParams p
        (->embedding-params {:model "text-embedding-3-small"
                             :input "hello"
                             :dimensions 256
                             :user "u1"})]
    (is (= "text-embedding-3-small" (str (.model p))))
    (is (= "hello" (-> p .input .asString)))
    (is (= 256 (opt (.dimensions p))))
    (is (= "u1" (opt (.user p)))))
  (testing "vector input becomes array-of-strings"
    (let [^com.openai.models.embeddings.EmbeddingCreateParams p
          (->embedding-params {:model "text-embedding-3-small" :input ["a" "b"]})]
      (is (= ["a" "b"] (vec (-> p .input .asArrayOfStrings))))))
  (testing "missing keys throw"
    (is (= {:openai/error :missing-key :key :model}
           (ex-data-for #(->embedding-params {:input "x"}))))
    (is (= {:openai/error :missing-key :key :input}
           (ex-data-for #(->embedding-params {:model "m"}))))))

(deftest maps-embedding-response
  (let [emb (fn [idx vs]
              (-> (com.openai.models.embeddings.Embedding/builder)
                  (.index (int idx))
                  (.embedding ^java.util.List (mapv float vs))
                  (.build)))
        resp (-> (com.openai.models.embeddings.CreateEmbeddingResponse/builder)
                 (.model "text-embedding-3-small")
                 (.data [(emb 1 [0.3 0.4]) (emb 0 [0.1 0.2])])
                 (.usage (-> (com.openai.models.embeddings.CreateEmbeddingResponse$Usage/builder)
                             (.promptTokens 7)
                             (.totalTokens 7)
                             (.build)))
                 (.build))
        m (embedding-response->map resp)]
    (is (= "text-embedding-3-small" (:model m)))
    (is (= {:prompt-tokens 7 :total-tokens 7} (:usage m)))
    (testing "embeddings are ordered by index regardless of wire order"
      (is (= [[(float 0.1) (float 0.2)] [(float 0.3) (float 0.4)]]
             (:embeddings m))))))

(deftest client-accepts-azure-service-version
  (is (instance? com.openai.client.OpenAIClient
                 (openai/client {:api-key "sk-test"
                                 :base-url "https://example.openai.azure.com"
                                 :azure-service-version "2024-10-21"}))))

(deftest translates-compound-file-search-filters
  (testing "and/or of comparison filters"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :and
                                                            :filters [{:type :eq :key "kind" :value "docs"}
                                                                      {:type :gte :key "year" :value 2024}]}}]}))))
          fs (opt (.filters (.asFileSearch t)))
          cf (.asCompoundFilter fs)]
      (is (.isCompoundFilter fs))
      (is (= "and" (.asString (.type cf))))
      (let [[a b] (vec (.filters cf))]
        (is (= "kind" (.key (opt (.comparison a)))))
        (is (= "eq" (.asString (.type (opt (.comparison a))))))
        (is (= "year" (.key (opt (.comparison b)))))
        (is (= 2024.0 (opt (.number (.value (opt (.comparison b))))))))))
  (testing "nested compound filter"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :or
                                                            :filters [{:type :eq :key "kind" :value "docs"}
                                                                      {:type :and
                                                                       :filters [{:type :eq :key "team" :value "core"}
                                                                                 {:type :ne :key "draft" :value true}]}]}}]}))))
          cf (.asCompoundFilter (opt (.filters (.asFileSearch t))))
          [_ nested] (vec (.filters cf))
          j (opt (.jsonValue nested))
          nm (.convert j java.util.Map)]
      (is (= "or" (.asString (.type cf))))
      (is (some? nm))
      (is (= "and" (get nm "type")))
      (is (= 2 (count (get nm "filters"))))
      (is (= {"type" "eq" "key" "team" "value" "core"}
             (into {} (first (get nm "filters")))))))
  (testing "plain comparison map is still a comparison filter"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]
                                                  :filters {:type :eq :key "kind" :value "docs"}}]}))))
          fs (opt (.filters (.asFileSearch t)))]
      (is (.isComparisonFilter fs)))))

(def ->batch-create-params #'openai/->batch-create-params)
(def batch->map #'openai/batch->map)
(def ->batch-list-params #'openai/->batch-list-params)

(deftest translates-batch-create-params
  (let [^com.openai.models.batches.BatchCreateParams p
        (->batch-create-params {:input-file-id "file_1"
                                :endpoint "/v1/responses"
                                :completion-window "24h"
                                :metadata {:job "nightly"}
                                :output-expires-after {:seconds 3600}})]
    (is (= "file_1" (.inputFileId p)))
    (is (= "/v1/responses" (.asString (.endpoint p))))
    (is (= "24h" (.asString (.completionWindow p))))
    (is (= "nightly" (-> p .metadata opt ._additionalProperties (get "job") .asStringOrThrow)))
    (is (= 3600 (-> p .outputExpiresAfter opt .seconds))))
  (testing "completion window defaults to 24h"
    (let [^com.openai.models.batches.BatchCreateParams p
          (->batch-create-params {:input-file-id "file_1" :endpoint "/v1/responses"})]
      (is (= "24h" (.asString (.completionWindow p))))))
  (testing "missing keys throw"
    (is (= {:openai/error :missing-key :key :input-file-id}
           (ex-data-for #(->batch-create-params {:endpoint "/v1/responses"}))))
    (is (= {:openai/error :missing-key :key :endpoint}
           (ex-data-for #(->batch-create-params {:input-file-id "file_1"}))))))

(deftest maps-batch-to-clojure
  (let [batch (-> (com.openai.models.batches.Batch/builder)
                  (.id "batch_1")
                  (.completionWindow "24h")
                  (.createdAt 123)
                  (.endpoint "/v1/responses")
                  (.inputFileId "file_in")
                  (.status com.openai.models.batches.Batch$Status/COMPLETED)
                  (.outputFileId "file_out")
                  (.errorFileId "file_err")
                  (.requestCounts (-> (com.openai.models.batches.BatchRequestCounts/builder)
                                      (.completed 9) (.failed 1) (.total 10)
                                      (.build)))
                  (.build))
        m (batch->map batch)]
    (is (= {:id "batch_1"
            :status :completed
            :endpoint "/v1/responses"
            :input-file-id "file_in"
            :completion-window "24h"
            :created-at 123
            :output-file-id "file_out"
            :error-file-id "file_err"
            :request-counts {:completed 9 :failed 1 :total 10}}
           m)))
  (testing "optional fields are absent when unset"
    (let [m (batch->map (-> (com.openai.models.batches.Batch/builder)
                            (.id "batch_2")
                            (.completionWindow "24h")
                            (.createdAt 124)
                            (.endpoint "/v1/embeddings")
                            (.inputFileId "file_in2")
                            (.status com.openai.models.batches.Batch$Status/IN_PROGRESS)
                            (.build)))]
      (is (= :in-progress (:status m)))
      (is (not (contains? m :output-file-id)))
      (is (not (contains? m :request-counts))))))

(deftest translates-batch-list-params
  (let [^com.openai.models.batches.BatchListParams p
        (->batch-list-params {:after "batch_0" :limit 5})]
    (is (= "batch_0" (opt (.after p))))
    (is (= 5 (opt (.limit p))))))

(def ->file-create-params #'openai/->file-create-params)
(def file->map #'openai/file->map)
(def ->file-list-params #'openai/->file-list-params)

(deftest translates-file-create-params
  (let [tmp (java.io.File/createTempFile "openai-clj" ".jsonl")]
    (spit tmp "{\"custom_id\":\"1\"}\n")
    (try
      (let [^com.openai.models.files.FileCreateParams p
            (->file-create-params {:file (.toPath tmp) :purpose :batch})]
        (is (= "batch" (.asString (.purpose p))))
        (is (some? (.file p))))
      (testing "string path, byte array, and input stream are accepted"
        (let [^com.openai.models.files.FileCreateParams p1
              (->file-create-params {:file (.getPath tmp) :purpose :batch})
              ^com.openai.models.files.FileCreateParams p2
              (->file-create-params {:file (.getBytes "data") :purpose :batch :filename "d.jsonl"})
              ^com.openai.models.files.FileCreateParams p3
              (->file-create-params {:file (java.io.ByteArrayInputStream. (.getBytes "data"))
                                     :purpose :batch :filename "d.jsonl"})]
          (is (some? (.file p1)))
          (is (= "d.jsonl" (-> p2 ._file .filename opt)))
          (is (= "d.jsonl" (-> p3 ._file .filename opt)))))
      (testing "expires-after"
        (let [^com.openai.models.files.FileCreateParams p
              (->file-create-params {:file (.toPath tmp) :purpose :batch
                                     :expires-after {:seconds 7200}})]
          (is (= 7200 (-> p .expiresAfter opt .seconds)))))
      (testing "missing keys throw"
        (is (= {:openai/error :missing-key :key :file}
               (ex-data-for #(->file-create-params {:purpose :batch}))))
        (is (= {:openai/error :missing-key :key :purpose}
               (ex-data-for #(->file-create-params {:file (.toPath tmp)})))))
      (finally (.delete tmp)))))

(deftest maps-file-object-to-clojure
  (let [f (-> (com.openai.models.files.FileObject/builder)
              (.id "file_1")
              (.bytes 10)
              (.createdAt 123)
              (.filename "a.jsonl")
              (.purpose com.openai.models.files.FileObject$Purpose/BATCH)
              (.status com.openai.models.files.FileObject$Status/PROCESSED)
              (.expiresAt 999)
              (.build))
        m (file->map f)]
    (is (= {:id "file_1"
            :bytes 10
            :created-at 123
            :filename "a.jsonl"
            :purpose :batch
            :status :processed
            :expires-at 999}
           m)))
  (testing "expires-at absent when unset"
    (is (not (contains? (file->map (-> (com.openai.models.files.FileObject/builder)
                                       (.id "f") (.bytes 1) (.createdAt 1)
                                       (.filename "x")
                                       (.purpose com.openai.models.files.FileObject$Purpose/ASSISTANTS)
                                       (.status com.openai.models.files.FileObject$Status/UPLOADED)
                                       (.build)))
                        :expires-at)))))

(deftest translates-file-list-params
  (let [^com.openai.models.files.FileListParams p
        (->file-list-params {:purpose "batch" :order :desc :after "file_0" :limit 3})]
    (is (= "batch" (opt (.purpose p))))
    (is (= "desc" (.asString (opt (.order p)))))
    (is (= "file_0" (opt (.after p))))
    (is (= 3 (opt (.limit p))))))

(deftest translates-chat-message-roles-and-content
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :system :content "sys"}
                                   {:role :developer :content "dev"}
                                   {:role :user :content "hi"}
                                   {:role :assistant :content "there"}
                                   {:role :tool :tool-call-id "call_1" :content "ok"}]})
        messages (.messages p)]
    (is (= "gpt-4o-mini" (.asString (.model p))))
    (is (.isSystem ^ChatCompletionMessageParam (first messages)))
    (is (.isDeveloper ^ChatCompletionMessageParam (second messages)))
    (is (.isUser ^ChatCompletionMessageParam (nth messages 2)))
    (is (.isAssistant ^ChatCompletionMessageParam (nth messages 3)))
    (is (.isTool ^ChatCompletionMessageParam (nth messages 4)))
    (is (= "sys" (-> ^ChatCompletionMessageParam (first messages) .asSystem .content .asText)))
    (is (= "dev" (-> ^ChatCompletionMessageParam (second messages) .asDeveloper .content .asText)))
    (is (= "hi" (-> ^ChatCompletionMessageParam (nth messages 2) .asUser .content .asText)))
    (is (= "there" (-> ^ChatCompletionMessageParam (nth messages 3) .asAssistant .content opt .asText)))
    (is (= "call_1" (-> ^ChatCompletionMessageParam (nth messages 4) .asTool .toolCallId)))))

(deftest translates-chat-user-content-parts
  (let [parts (-> (chat-params {:model "gpt-4o-mini"
                                :messages [{:role :user
                                            :content [{:type :text :text "look"}
                                                      {:type :image
                                                       :image-url "https://example.test/image.png"
                                                       :detail :high}
                                                      {:type :input-audio
                                                       :data "AAAA"
                                                       :format :wav}]}]})
                  .messages
                  first
                  .asUser
                  .content
                  .asArrayOfContentParts)
        text-part (.asText ^ChatCompletionContentPart (first parts))
        image-part (.asImageUrl ^ChatCompletionContentPart (second parts))
        audio-part (.asInputAudio ^ChatCompletionContentPart (nth parts 2))]
    (is (= "look" (.text text-part)))
    (is (= "https://example.test/image.png" (-> image-part .imageUrl .url)))
    (is (= "high" (-> image-part .imageUrl .detail opt .asString)))
    (is (= "AAAA" (-> audio-part .inputAudio .data)))
    (is (= "wav" (-> audio-part .inputAudio .format .asString)))))

(deftest translates-chat-scalar-options
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "hi"}]
                        :temperature 0.1
                        :top-p 0.9
                        :max-tokens 64
                        :max-completion-tokens 32
                        :n 2
                        :stop ["END" "DONE"]
                        :presence-penalty 0.2
                        :frequency-penalty 0.3
                        :logit-bias {"42" -100}
                        :seed 123
                        :user "user_1"
                        :metadata {:app "tests"}
                        :store false
                        :service-tier :priority
                        :parallel-tool-calls true
                        :logprobs true
                        :top-logprobs 2
                        :reasoning-effort :low
                        :stream-options {:include-usage true}})]
    (is (= 0.1 (opt (.temperature p))))
    (is (= 0.9 (opt (.topP p))))
    (is (= 64 (opt (.maxTokens p))))
    (is (= 32 (opt (.maxCompletionTokens p))))
    (is (= 2 (opt (.n p))))
    (is (= ["END" "DONE"] (.asStrings (opt (.stop p)))))
    (is (= 0.2 (opt (.presencePenalty p))))
    (is (= 0.3 (opt (.frequencyPenalty p))))
    (is (= -100 (.convert (get (._additionalProperties (opt (.logitBias p))) "42") Integer)))
    (is (= 123 (opt (.seed p))))
    (is (= "user_1" (opt (.user p))))
    (is (= "tests" (.asStringOrThrow (get (._additionalProperties (opt (.metadata p))) "app"))))
    (is (false? (opt (.store p))))
    (is (= "priority" (.asString (opt (.serviceTier p)))))
    (is (true? (opt (.parallelToolCalls p))))
    (is (true? (opt (.logprobs p))))
    (is (= 2 (opt (.topLogprobs p))))
    (is (= "low" (.asString (opt (.reasoningEffort p)))))
    (is (true? (-> p .streamOptions opt .includeUsage opt)))))

(deftest translates-chat-tools-tool-choice-and-response-format
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "weather"}]
                        :tools [{:type :function
                                 :name "get_weather"
                                 :description "Get weather"
                                 :strict true
                                 :parameters {:type "object"
                                              :properties {:location {:type "string"}}
                                              :required ["location"]}}]
                        :tool-choice {:type :function :name "get_weather"}
                        :response-format {:type :json-schema
                                          :json-schema {:name "answer"
                                                        :description "Answer"
                                                        :strict true
                                                        :schema {:type "object"
                                                                 :properties {:answer {:type "string"}}
                                                                 :required ["answer"]}}}})
        tool (first (opt (.tools p)))
        f (.function (.asFunction tool))
        params-props (._additionalProperties (opt (.parameters f)))
        tc (opt (.toolChoice p))
        rf (-> p .responseFormat opt .asJsonSchema .jsonSchema)
        schema-props (._additionalProperties (opt (.schema rf)))]
    (is (.isFunction tool))
    (is (= "get_weather" (.name f)))
    (is (= "Get weather" (opt (.description f))))
    (is (true? (opt (.strict f))))
    (is (= "object" (.asStringOrThrow (get params-props "type"))))
    (is (.isNamedToolChoice tc))
    (is (= "get_weather" (-> tc .asNamedToolChoice .function .name)))
    (is (= "answer" (.name rf)))
    (is (= "Answer" (opt (.description rf))))
    (is (true? (opt (.strict rf))))
    (is (= "object" (.asStringOrThrow (get schema-props "type"))))))

(deftest translates-chat-required-keys-and-unknown-keys
  (is (= {:openai/error :missing-key :key :model}
         (ex-data-for #(chat-params {:messages [{:role :user :content "hi"}]}))))
  (is (= {:openai/error :missing-key :key :messages}
         (ex-data-for #(chat-params {:model "gpt-4o-mini"}))))
  (let [p (chat-params {:model "gpt-4o-mini"
                        :messages [{:role :user :content "hi"}]
                        :unknown "ignored"})]
    (is (= "gpt-4o-mini" (.asString (.model p))))
    (is (= 1 (count (.messages p))))))

(defn- chat-tool-call [args]
  (ChatCompletionMessageToolCall/ofFunction
   (-> (ChatCompletionMessageFunctionToolCall/builder)
       (.id "call_1")
       (.function (-> (ChatCompletionMessageFunctionToolCall$Function/builder)
                      (.name "get_weather")
                      (.arguments args)
                      (.build)))
       (.build))))

(defn- chat-completion [args]
  (-> (ChatCompletion/builder)
      (.id "chatcmpl_1")
      (.model "gpt-4o-mini")
      (.created 1790000000)
      (.serviceTier ChatCompletion$ServiceTier/DEFAULT)
      (.usage (-> (CompletionUsage/builder)
                  (.promptTokens 10)
                  (.completionTokens 20)
                  (.totalTokens 30)
                  (.build)))
      (.choices [(-> (ChatCompletion$Choice/builder)
                     (.index 0)
                     (.finishReason ChatCompletion$Choice$FinishReason/TOOL_CALLS)
                     (.logprobs (java.util.Optional/empty))
                     (.message (-> (ChatCompletionMessage/builder)
                                   (.role (com.openai.core.JsonValue/from "assistant"))
                                   (.content "Use get_weather.")
                                   (.refusal "no")
                                   (.toolCalls [(chat-tool-call args)])
                                   (.build)))
                     (.build))])
      (.build)))

(deftest maps-chat-completion-to-clojure
  (let [m (chat-completion->map (chat-completion "{\"location\":\"Denver\"}"))]
    (is (= {:id "chatcmpl_1"
            :model "gpt-4o-mini"
            :created 1790000000
            :choices [{:index 0
                       :finish-reason :tool-calls
                       :message {:role :assistant
                                 :content "Use get_weather."
                                 :tool-calls [{:id "call_1"
                                               :type :function
                                               :function {:name "get_weather"
                                                          :arguments {:location "Denver"}}}]
                                 :refusal "no"}}]
            :usage {:prompt-tokens 10
                    :completion-tokens 20
                    :total-tokens 30}
            :text "Use get_weather."
            :service-tier :default}
           m))))

(deftest chat-completion-map-keeps-garbage-arguments-raw
  (let [m (chat-completion->map (chat-completion "{not json}"))]
    (is (= "{not json}" (-> m :choices first :message :tool-calls first :function :arguments)))))

(deftest maps-chat-stream-chunk-to-clojure
  (let [chunk (-> (ChatCompletionChunk/builder)
                  (.id "chunk_1")
                  (.model "gpt-4o-mini")
                  (.created 1790000001)
                  (.choices [(-> (ChatCompletionChunk$Choice/builder)
                                 (.index 0)
                                 (.finishReason ChatCompletionChunk$Choice$FinishReason/STOP)
                                 (.delta (-> (ChatCompletionChunk$Choice$Delta/builder)
                                             (.role ChatCompletionChunk$Choice$Delta$Role/ASSISTANT)
                                             (.content "Hel")
                                             (.toolCalls [(-> (ChatCompletionChunk$Choice$Delta$ToolCall/builder)
                                                              (.index 0)
                                                              (.id "call_1")
                                                              (.type ChatCompletionChunk$Choice$Delta$ToolCall$Type/FUNCTION)
                                                              (.function (-> (ChatCompletionChunk$Choice$Delta$ToolCall$Function/builder)
                                                                             (.name "get_weather")
                                                                             (.arguments "{\"")
                                                                             (.build)))
                                                              (.build))])
                                             (.build)))
                                 (.build))])
                  (.build))]
    (is (= {:type :chunk
            :choices [{:index 0
                       :finish-reason :stop
                       :delta {:role :assistant
                               :content "Hel"
                               :tool-calls [{:index 0
                                             :id "call_1"
                                             :type :function
                                             :function {:name "get_weather"
                                                        :arguments "{\""}}]}}]}
           (chat-chunk->map chunk)))))

(deftest maps-chat-stream-usage-only-chunk
  (let [chunk (-> (ChatCompletionChunk/builder)
                  (.id "chunk_2")
                  (.model "gpt-4o-mini")
                  (.created 1790000002)
                  (.choices [])
                  (.usage (-> (CompletionUsage/builder)
                              (.promptTokens 1)
                              (.completionTokens 2)
                              (.totalTokens 3)
                              (.build)))
                  (.build))]
    (is (= {:type :chunk
            :choices []
            :usage {:prompt-tokens 1
                    :completion-tokens 2
                    :total-tokens 3}}
           (chat-chunk->map chunk)))))

(deftest builds-stored-resource-params
  (let [model-p (model-delete-params "ft:model")
        update-p (chat-completion-update-params "chatcmpl_1" {:metadata {:team "sdk"}})
        list-p (chat-completion-list-params {:model "gpt-4o-mini"
                                             :metadata {:team "sdk"}
                                             :after "chatcmpl_0"
                                             :limit 25
                                             :order :asc})
        messages-p (chat-completion-message-list-params "chatcmpl_1"
                                                        {:after "msg_0" :limit 10 :order :desc})]
    (is (= "ft:model" (opt (.model model-p))))
    (is (= "chatcmpl_1" (opt (.completionId update-p))))
    (is (= "sdk" (-> update-p .metadata opt ._additionalProperties (get "team") .asStringOrThrow)))
    (is (= "gpt-4o-mini" (opt (.model list-p))))
    (is (= ["sdk"] (-> list-p .metadata opt ._additionalProperties (.values "team"))))
    (is (= "chatcmpl_0" (opt (.after list-p))))
    (is (= 25 (opt (.limit list-p))))
    (is (= "asc" (-> list-p .order opt .asString)))
    (is (= "chatcmpl_1" (opt (.completionId messages-p))))
    (is (= "msg_0" (opt (.after messages-p))))
    (is (= 10 (opt (.limit messages-p))))
    (is (= "desc" (-> messages-p .order opt .asString)))))

(deftest maps-stored-resource-results
  (is (= {:id "ft:model" :deleted true}
         (deleted-model->map (-> (com.openai.models.models.ModelDeleted/builder)
                                 (.id "ft:model")
                                 (.deleted true)
                                 (.object_ "model")
                                 (.build)))))
  (is (= {:id "chatcmpl_1" :deleted false}
         (deleted-chat-completion->map
          (-> (com.openai.models.chat.completions.ChatCompletionDeleted/builder)
              (.id "chatcmpl_1")
              (.deleted false)
              (.object_ (com.openai.core.JsonValue/from "chat.completion.deleted"))
              (.build)))))
  (is (= {:id "msg_1"
          :role :assistant
          :content "Calling a tool"
          :refusal "no"
          :tool-calls [{:id "call_1"
                        :type :function
                        :function {:name "get_weather"
                                   :arguments {:location "Denver"}}}]}
         (stored-chat-message->map
          (-> (ChatCompletionStoreMessage/builder)
              (.id "msg_1")
              (.role (com.openai.core.JsonValue/from "assistant"))
              (.content "Calling a tool")
              (.refusal "no")
              (.toolCalls [(chat-tool-call "{\"location\":\"Denver\"}")])
              (.build))))))
