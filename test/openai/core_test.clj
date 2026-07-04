(ns openai.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.core :as openai])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.models Model)
           (com.openai.models.responses ResponseCreateParams
                                        ResponseCreateParams$ToolChoice
                                        ResponseCompletedEvent
                                        ResponseCreatedEvent
                                        ResponseErrorEvent
                                        ResponseFailedEvent
                                        ResponseFunctionToolCall
                                        ResponseFunctionCallArgumentsDeltaEvent
                                        ResponseFunctionCallArgumentsDoneEvent
                                        ResponseIncompleteEvent
                                        ResponseInProgressEvent
                                        ResponseInputItem
                                        ResponseOutputItemAddedEvent
                                        ResponseOutputItemDoneEvent
                                        ResponseOutputItem
                                        ResponseOutputItem$ImageGenerationCall
                                        ResponseOutputItem$ImageGenerationCall$Status
                                        ResponseOutputMessage
                                        ResponseOutputMessage$Content
                                        ResponseOutputMessage$Status
                                        ResponseOutputRefusal
                                        ResponseOutputText
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
                          :base-url "https://example.test/v1"})]
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
                   :temperature 0.7
                   :top-p 0.9
                   :previous-response-id "resp_123"
                   :store false
                   :user "end-user-id"
                   :reasoning {:effort :medium}})]
    (is (= "follow these" (opt (.instructions p))))
    (is (= 512 (opt (.maxOutputTokens p))))
    (is (= 0.7 (opt (.temperature p))))
    (is (= 0.9 (opt (.topP p))))
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
           (ex-data-for #(params {:model "gpt-5.2"}))))))

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
                                         :tools [{:type :web-search}]}))))]
      (is (.isWebSearch t))))
  (testing "file search tool"
    (let [t (first (opt (.tools (params {:model "gpt-5.2"
                                         :input "hi"
                                         :tools [{:type :file-search
                                                  :vector-store-ids ["vs_1"]}]}))))]
      (is (.isFileSearch t))
      (is (= ["vs_1"] (vec (.vectorStoreIds (.asFileSearch t)))))))
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

(defn- text-content [s]
  (ResponseOutputMessage$Content/ofOutputText
   (-> (ResponseOutputText/builder)
       (.text s)
       (.annotations [])
       (.build))))

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
       (.result "")
       (.status (ResponseOutputItem$ImageGenerationCall$Status/of "completed"))
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

(deftest maps-response-to-clojure
  (let [m (response->map (response [(message-item)
                                    (function-call-item "{\"location\":\"Denver\"}")
                                    (unknown-item)]))]
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
            {:type :unknown}]
           (:output m)))))

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
          :item {:type :unknown}
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
