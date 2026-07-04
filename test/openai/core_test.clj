(ns openai.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.core :as openai])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.responses ResponseCreateParams
                                        ResponseCreateParams$ToolChoice
                                        ResponseFunctionToolCall
                                        ResponseInputItem
                                        ResponseOutputItem
                                        ResponseOutputItem$ImageGenerationCall
                                        ResponseOutputItem$ImageGenerationCall$Status
                                        ResponseOutputMessage
                                        ResponseOutputMessage$Content
                                        ResponseOutputMessage$Status
                                        ResponseOutputRefusal
                                        ResponseOutputText
                                        ResponseStatus
                                        ResponseUsage
                                        ResponseUsage$InputTokensDetails
                                        ResponseUsage$OutputTokensDetails
                                        ToolChoiceOptions)))

(defn- params ^ResponseCreateParams [m]
  (#'openai/->params m))

(defn- response->map [r]
  (#'openai/response->map r))

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
