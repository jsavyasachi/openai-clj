(ns openai.core
  "Idiomatic Clojure wrapper over the official OpenAI Java SDK
  (`com.openai/openai-java`), focused on the Responses API."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.client.okhttp OpenAIOkHttpClient
                                      OpenAIOkHttpClient$Builder)
           (com.openai.core JsonValue)
           (com.openai.core.http StreamResponse)
           (com.openai.models Reasoning
                              Reasoning$Builder
                              ReasoningEffort
                              ResponsesModel)
           (com.openai.models.models Model
                                      ModelListPage)
           (com.openai.models.responses EasyInputMessage
                                         EasyInputMessage$Builder
                                         EasyInputMessage$Role
                                         FileSearchTool
                                         FileSearchTool$Builder
                                         FunctionTool
                                         FunctionTool$Builder
                                         FunctionTool$Parameters
                                         FunctionTool$Parameters$Builder
                                         Response
                                         ResponseCreateParams
                                         ResponseCreateParams$Builder
                                         ResponseCreateParams$Input
                                         ResponseCreateParams$Metadata
                                         ResponseCreateParams$Metadata$Builder
                                         ResponseCreateParams$ToolChoice
                                         ResponseCompletedEvent
                                         ResponseError
                                         ResponseErrorEvent
                                         ResponseFailedEvent
                                         ResponseFunctionCallArgumentsDeltaEvent
                                         ResponseFunctionCallArgumentsDoneEvent
                                         ResponseFunctionToolCall
                                         ResponseFunctionWebSearch
                                         ResponseIncompleteEvent
                                         ResponseInputItem
                                         ResponseInputItem$FunctionCallOutput
                                         ResponseInputItem$FunctionCallOutput$Builder
                                         ResponseOutputItemAddedEvent
                                         ResponseOutputItemDoneEvent
                                         ResponseOutputItem
                                         ResponseOutputMessage
                                         ResponseOutputMessage$Content
                                         ResponseOutputRefusal
                                         ResponseOutputText
                                         ResponseReasoningItem
                                         ResponseReasoningItem$Status
                                         ResponseReasoningTextDeltaEvent
                                         ResponseReasoningTextDoneEvent
                                         ResponseRefusalDeltaEvent
                                         ResponseRefusalDoneEvent
                                         ResponseStatus
                                         ResponseStreamEvent
                                         ResponseTextDeltaEvent
                                         ResponseTextDoneEvent
                                         ResponseUsage
                                         Tool
                                         Tool$CodeInterpreter
                                         Tool$CodeInterpreter$Builder
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto$Builder
                                         ToolChoiceFunction
                                         ToolChoiceFunction$Builder
                                         ToolChoiceOptions
                                         WebSearchTool
                                         WebSearchTool$Type)
           (com.openai.services.blocking ModelService
                                         ResponseService)))

(set! *warn-on-reflection* true)

(defn client
  "An OpenAI client. With no args, resolves credentials from the environment
  (`OPENAI_API_KEY`). Pass explicit config keys to set client options."
  (^OpenAIClient [] (OpenAIOkHttpClient/fromEnv))
  (^OpenAIClient [{:keys [api-key organization project base-url]}]
   (let [^OpenAIOkHttpClient$Builder b (OpenAIOkHttpClient/builder)]
     (when api-key (.apiKey b ^String api-key))
     (when organization (.organization b ^String organization))
     (when project (.project b ^String project))
     (when base-url (.baseUrl b ^String base-url))
     (.build b))))

(defn- missing-key! [k]
  (throw (ex-info (str "Missing required key " k)
                  {:openai/error :missing-key :key k})))

(defn- ->metadata ^ResponseCreateParams$Metadata [m]
  (let [^ResponseCreateParams$Metadata$Builder b (ResponseCreateParams$Metadata/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from (str v))))
    (.build b)))

(defn- ->role ^EasyInputMessage$Role [role]
  (EasyInputMessage$Role/of (name role)))

(def ^:private json-mapper (json/object-mapper {:decode-key-fn true}))

(defn- encode-output [x]
  (if (string? x)
    x
    (json/write-value-as-string x)))

(defn- ->function-call-output ^ResponseInputItem [{:keys [call-id output]}]
  (let [^ResponseInputItem$FunctionCallOutput$Builder b
        (ResponseInputItem$FunctionCallOutput/builder)]
    (when-not call-id (missing-key! :call-id))
    (.callId b ^String call-id)
    (.output b ^String (encode-output output))
    (ResponseInputItem/ofFunctionCallOutput (.build b))))

(defn- ->input-message ^ResponseInputItem [{:keys [role content type] :as item}]
  (if (= :function-call-output (keyword type))
    (->function-call-output item)
    (let [^EasyInputMessage$Builder b (EasyInputMessage/builder)]
      (.role b (->role role))
      (.content b ^String content)
      (ResponseInputItem/ofEasyInputMessage (.build b)))))

(defn- ->input ^ResponseCreateParams$Input [input]
  (if (string? input)
    (ResponseCreateParams$Input/ofText input)
    (ResponseCreateParams$Input/ofResponse
     ^java.util.List (mapv ->input-message input))))

(defn- ->reasoning ^Reasoning [{:keys [effort]}]
  (let [^Reasoning$Builder b (Reasoning/builder)]
    (when effort (.effort b (ReasoningEffort/of (name effort))))
    (.build b)))

(defn- ->function-parameters ^FunctionTool$Parameters [m]
  (let [^FunctionTool$Parameters$Builder b (FunctionTool$Parameters/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from v)))
    (.build b)))

(defn- ->function-tool ^FunctionTool [{:keys [name description parameters strict]}]
  (let [^FunctionTool$Builder b (FunctionTool/builder)]
    (when-not name (missing-key! :name))
    (.name b ^String name)
    (when description (.description b ^String description))
    (when parameters (.parameters b (->function-parameters parameters)))
    (when (some? strict) (.strict b (boolean strict)))
    (.build b)))

(defn- ->code-interpreter ^Tool$CodeInterpreter [{:keys [container]}]
  (let [^Tool$CodeInterpreter$Builder b (Tool$CodeInterpreter/builder)]
    (if container
      (.container b ^String container)
      (.container b
                  (let [^Tool$CodeInterpreter$Container$CodeInterpreterToolAuto$Builder ab
                        (Tool$CodeInterpreter$Container$CodeInterpreterToolAuto/builder)]
                    (.build ab))))
    (.build b)))

(defn- ->file-search-tool ^FileSearchTool [{:keys [vector-store-ids]}]
  (let [^FileSearchTool$Builder b (FileSearchTool/builder)]
    (when-not (seq vector-store-ids) (missing-key! :vector-store-ids))
    (.vectorStoreIds b ^java.util.List (vec vector-store-ids))
    (.build b)))

(defn- ->tool ^Tool [{:keys [type] :as tool}]
  (case (keyword type)
    :function (Tool/ofFunction (->function-tool tool))
    :web-search (Tool/ofWebSearch (-> (WebSearchTool/builder)
                                       (.type WebSearchTool$Type/WEB_SEARCH)
                                       (.build)))
    :file-search (Tool/ofFileSearch (->file-search-tool tool))
    :code-interpreter (Tool/ofCodeInterpreter (->code-interpreter tool))
    (throw (ex-info (str "Unknown tool type " type)
                    {:openai/error :unknown-tool-type :type type}))))

(defn- ->tool-choice ^ResponseCreateParams$ToolChoice [choice]
  (if (map? choice)
    (case (keyword (:type choice))
      :function (ResponseCreateParams$ToolChoice/ofFunction
                 (let [^ToolChoiceFunction$Builder b (ToolChoiceFunction/builder)]
                   (when-not (:name choice) (missing-key! :name))
                   (.name b ^String (:name choice))
                   (.build b)))
      (throw (ex-info (str "Unknown tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type
                       :type (:type choice)})))
    (ResponseCreateParams$ToolChoice/ofOptions
     (case (keyword choice)
       :auto ToolChoiceOptions/AUTO
       :required ToolChoiceOptions/REQUIRED
       :none ToolChoiceOptions/NONE
       (throw (ex-info (str "Unknown tool choice " choice)
                       {:openai/error :unknown-tool-choice
                        :tool-choice choice}))))))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p
           metadata previous-response-id store reasoning user tools tool-choice
           parallel-tool-calls]}]
  (when-not model (missing-key! :model))
  (when-not input (missing-key! :input))
  (let [^ResponseCreateParams$Builder b (ResponseCreateParams/builder)]
    (.model b ^String model)
    (.input b (->input input))
    (when instructions (.instructions b ^String instructions))
    (when max-output-tokens (.maxOutputTokens b (long max-output-tokens)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when metadata (.metadata b (->metadata metadata)))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? store) (.store b (boolean store)))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (when user (.user b ^String user))
    (doseq [t tools] (.addTool b (->tool t)))
    (when tool-choice (.toolChoice b (->tool-choice tool-choice)))
    (when (some? parallel-tool-calls)
      (.parallelToolCalls b (boolean parallel-tool-calls)))
    (.build b)))

(defn- ->keyword [s]
  (-> s str str/lower-case (str/replace "_" "-") keyword))

(defn- parse-arguments [^String s]
  (try
    (json/read-value s json-mapper)
    (catch Exception _
      s)))

(defn- content->map [^ResponseOutputMessage$Content c]
  (cond
    (.isOutputText c) (let [^ResponseOutputText t (.asOutputText c)]
                        {:type :text :text (.text t)})
    (.isRefusal c) (let [^ResponseOutputRefusal r (.asRefusal c)]
                     {:type :refusal :refusal (.refusal r)})
    :else {:type :unknown}))

(defn- message->map [^ResponseOutputMessage m]
  {:type :message
   :role :assistant
   :id (.id m)
   :content (mapv content->map (.content m))})

(defn- function-call->map [^ResponseFunctionToolCall f]
  (cond-> {:type :function-call
           :name (.name f)
           :call-id (.callId f)
           :arguments (parse-arguments (.arguments f))}
    (.isPresent (.id f)) (assoc :id (.get (.id f)))))

(defn- reasoning->map [^ResponseReasoningItem r]
  (cond-> {:type :reasoning}
    (.isPresent (.status r)) (assoc :status (->keyword (.asString ^ResponseReasoningItem$Status (.get (.status r)))))))

(defn- web-search-call->map [^ResponseFunctionWebSearch c]
  {:type :web-search-call :status (->keyword (.asString (.status c)))})

(defn- file-search-call->map [^com.openai.models.responses.ResponseFileSearchToolCall c]
  {:type :file-search-call :status (->keyword (.asString (.status c)))})

(defn- code-interpreter-call->map [^com.openai.models.responses.ResponseCodeInterpreterToolCall c]
  {:type :code-interpreter-call :status (->keyword (.asString (.status c)))})

(defn- output-item->map [^ResponseOutputItem item]
  (cond
    (.isMessage item) (message->map (.asMessage item))
    (.isFunctionCall item) (function-call->map (.asFunctionCall item))
    (.isReasoning item) (reasoning->map (.asReasoning item))
    (.isWebSearchCall item) (web-search-call->map (.asWebSearchCall item))
    (.isFileSearchCall item) (file-search-call->map (.asFileSearchCall item))
    (.isCodeInterpreterCall item) (code-interpreter-call->map (.asCodeInterpreterCall item))
    :else {:type :unknown}))

(defn- usage->map [^ResponseUsage u]
  {:input-tokens (.inputTokens u)
   :output-tokens (.outputTokens u)
   :total-tokens (.totalTokens u)})

(defn- error->map [^ResponseError e]
  {:code (->keyword (.asString (.code e)))
   :message (.message e)})

(defn- output-text [items]
  (apply str
         (for [item items
               :when (= :message (:type item))
               content (:content item)
               :when (= :text (:type content))]
           (:text content))))

(defn- response->map [^Response r]
  (let [items (mapv output-item->map (.output r))]
    (cond-> {:id (.id r)
             :model (.asString ^ResponsesModel (.model r))
             :output items
             :text (output-text items)
             :created-at (.createdAt r)}
      (.isPresent (.status r)) (assoc :status (->keyword (.asString ^ResponseStatus (.get (.status r)))))
      (.isPresent (.usage r)) (assoc :usage (usage->map (.get (.usage r))))
      (.isPresent (.error r)) (assoc :error (error->map (.get (.error r))))
      (.isPresent (.incompleteDetails r)) (assoc :incomplete-details {})
      (.isPresent (.previousResponseId r)) (assoc :previous-response-id (.get (.previousResponseId r))))))

(defn create-response
  "Send a Responses API request and return a Clojure map.

  Request keys: `:model` (required string), `:input` (required string or vector),
  `:instructions`, `:max-output-tokens`, `:temperature`, `:top-p`, `:metadata`,
  `:previous-response-id`, `:store`, `:reasoning`, `:user`, `:tools`,
  `:tool-choice`, and `:parallel-tool-calls`.

  Message-vector input items accept `{:role :system|:developer|:user|:assistant
  :content \"...\"}` and `{:type :function-call-output :call-id \"...\"
  :output \"...\"}`. Map outputs are JSON-encoded.

  Tools: `{:type :function :name \"...\" :description \"...\" :strict true
  :parameters {...}}`, `{:type :web-search}`, `{:type :file-search
  :vector-store-ids [...]}`, or `{:type :code-interpreter :container \"...\"}`.
  Code interpreter defaults to an auto container when `:container` is omitted.

  Tool choice: `:auto`, `:required`, `:none`, or `{:type :function :name \"...\"}`.

  Returns `{:id :model :status :output :text :usage :created-at}` plus
  `:error`, `:incomplete-details`, or `:previous-response-id` when present.
  Output items are normalized to `:message`, `:function-call`, `:reasoning`,
  `:web-search-call`, `:file-search-call`, `:code-interpreter-call`, or
  `:unknown`."
  [^OpenAIClient client req]
  (response->map (.create (.responses client) (->params req))))

(defn- model->map [^Model m]
  {:id (.id m)
   :created (.created m)
   :owned-by (.ownedBy m)})

(defn list-models
  "List available models as a vector of `{:id :created :owned-by}` maps. Pages
  are followed automatically."
  [^OpenAIClient client]
  (let [^ModelService svc (.models client)
        ^ModelListPage p (.list svc)]
    (mapv model->map (.autoPager p))))

(defn get-model
  "Retrieve one model by id as a `{:id :created :owned-by}` map."
  [^OpenAIClient client ^String model-id]
  (let [^ModelService svc (.models client)]
    (model->map (.retrieve svc model-id))))

(defn- event->map
  "Normalize one `ResponseStreamEvent` into a Clojure map keyed by `:type`."
  [^ResponseStreamEvent ev]
  (cond
    (.isOutputTextDelta ev) (let [e ^ResponseTextDeltaEvent (.asOutputTextDelta ev)]
                              {:type :output-text-delta
                               :delta (.delta e)
                               :item-id (.itemId e)
                               :output-index (.outputIndex e)})
    (.isOutputTextDone ev) (let [e ^ResponseTextDoneEvent (.asOutputTextDone ev)]
                             {:type :output-text-done
                              :text (.text e)
                              :item-id (.itemId e)
                              :output-index (.outputIndex e)})
    (.isFunctionCallArgumentsDelta ev)
    (let [e ^ResponseFunctionCallArgumentsDeltaEvent (.asFunctionCallArgumentsDelta ev)]
      {:type :function-call-arguments-delta
       :delta (.delta e)
       :item-id (.itemId e)})
    (.isFunctionCallArgumentsDone ev)
    (let [e ^ResponseFunctionCallArgumentsDoneEvent (.asFunctionCallArgumentsDone ev)]
      {:type :function-call-arguments-done
       :arguments (.arguments e)
       :item-id (.itemId e)})
    (.isReasoningTextDelta ev) (let [e ^ResponseReasoningTextDeltaEvent (.asReasoningTextDelta ev)]
                                 {:type :reasoning-text-delta
                                  :delta (.delta e)})
    (.isReasoningTextDone ev) (let [e ^ResponseReasoningTextDoneEvent (.asReasoningTextDone ev)]
                                {:type :reasoning-text-done
                                 :text (.text e)})
    (.isRefusalDelta ev) (let [e ^ResponseRefusalDeltaEvent (.asRefusalDelta ev)]
                           {:type :refusal-delta
                            :delta (.delta e)})
    (.isRefusalDone ev) (let [e ^ResponseRefusalDoneEvent (.asRefusalDone ev)]
                          {:type :refusal-done
                           :refusal (.refusal e)})
    (.isOutputItemAdded ev) (let [e ^ResponseOutputItemAddedEvent (.asOutputItemAdded ev)]
                              {:type :output-item-added
                               :item (output-item->map (.item e))
                               :output-index (.outputIndex e)})
    (.isOutputItemDone ev) (let [e ^ResponseOutputItemDoneEvent (.asOutputItemDone ev)]
                             {:type :output-item-done
                              :item (output-item->map (.item e))
                              :output-index (.outputIndex e)})
    (.isCreated ev) {:type :created}
    (.isInProgress ev) {:type :in-progress}
    (.isCompleted ev) (let [e ^ResponseCompletedEvent (.asCompleted ev)]
                        {:type :completed
                         :response (response->map (.response e))})
    (.isIncomplete ev) (let [e ^ResponseIncompleteEvent (.asIncomplete ev)]
                         {:type :incomplete
                          :response (response->map (.response e))})
    (.isFailed ev) (let [e ^ResponseFailedEvent (.asFailed ev)]
                     {:type :failed
                      :response (response->map (.response e))})
    (.isError ev) (let [e ^ResponseErrorEvent (.asError ev)
                        code (.code e)]
                    (cond-> {:type :error
                             :message (.message e)}
                      (.isPresent code) (assoc :code (.get code))))
    :else {:type :other}))

(defn stream
  "Stream a Responses API request, invoking `on-event` with a normalized event
  map for every server-sent event as it arrives, and returning the concatenated
  output text. Takes the same `req` map as `create-response`. The underlying
  HTTP stream is closed automatically."
  ^String [^OpenAIClient client req on-event]
  (let [^ResponseService svc (.responses client)]
    (with-open [^StreamResponse sr (.createStreaming svc (->params req))]
      (let [sb (StringBuilder.)]
        (doseq [ev (iterator-seq (.iterator (.stream sr)))]
          (let [m (event->map ev)]
            (when (= :output-text-delta (:type m))
              (.append sb ^String (:delta m)))
            (when on-event (on-event m))))
        (str sb)))))

(defn stream-text
  "Stream a Responses API request, calling `on-text` with each output text delta
  string as it arrives, and returning the full concatenated text."
  ^String [^OpenAIClient client req on-text]
  (stream client req
          (fn [m] (when (and on-text (= :output-text-delta (:type m))) (on-text (:delta m))))))

(defn get-response
  "Retrieve one stored response by id as a response map."
  [^OpenAIClient client ^String response-id]
  (let [^ResponseService svc (.responses client)]
    (response->map (.retrieve svc response-id))))

(defn delete-response
  "Delete one stored response by id. The OpenAI Java SDK returns void."
  [^OpenAIClient client ^String response-id]
  (let [^ResponseService svc (.responses client)]
    (.delete svc response-id))
  nil)

(defn cancel-response
  "Cancel an in-progress response by id and return the resulting response map."
  [^OpenAIClient client ^String response-id]
  (let [^ResponseService svc (.responses client)]
    (response->map (.cancel svc response-id))))
