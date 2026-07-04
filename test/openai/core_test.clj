(ns openai.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.core :as openai])
  (:import (com.openai.client OpenAIClient)
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
