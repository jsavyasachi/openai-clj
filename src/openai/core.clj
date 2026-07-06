(ns openai.core
  "Idiomatic Clojure wrapper over the official OpenAI Java SDK
  (`com.openai/openai-java`), focused on the Responses API."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as json])
  (:import (com.openai.client OpenAIClient)
           (com.openai.client.okhttp OpenAIOkHttpClient
                                      OpenAIOkHttpClient$Builder)
           (com.openai.core JsonValue MultipartField)
           (com.openai.core.http StreamResponse)
           (com.openai.errors BadRequestException
                              InternalServerException
                              NotFoundException
                              OpenAIException
                              OpenAIIoException
                              OpenAIServiceException
                              PermissionDeniedException
                              RateLimitException
                              UnauthorizedException
                              UnexpectedStatusCodeException
                              UnprocessableEntityException)
           (java.time Duration)
           (com.openai.models ComparisonFilter
                              ComparisonFilter$Builder
                              ComparisonFilter$Type
                              CompoundFilter
                              CompoundFilter$Builder
                              CompoundFilter$Type
                              Reasoning
                              Reasoning$Builder
                              ReasoningEffort
                              ResponsesModel)
           (com.openai.models.batches Batch
                                      Batch$Status
                                      BatchCancelParams
                                      BatchCreateParams
                                      BatchCreateParams$Builder
                                      BatchCreateParams$CompletionWindow
                                      BatchCreateParams$Endpoint
                                      BatchCreateParams$Metadata
                                      BatchCreateParams$Metadata$Builder
                                      BatchCreateParams$OutputExpiresAfter
                                      BatchCreateParams$OutputExpiresAfter$Builder
                                      BatchListPage
                                      BatchListParams
                                      BatchListParams$Builder
                                      BatchRequestCounts)
           (com.openai.models.files FileCreateParams
                                    FileCreateParams$Builder
                                    FileCreateParams$ExpiresAfter
                                    FileCreateParams$ExpiresAfter$Builder
                                    FileDeleted
                                    FileListPage
                                    FileListParams
                                    FileListParams$Builder
                                    FileListParams$Order
                                    FileObject
                                    FilePurpose)
           (com.openai.azure AzureOpenAIServiceVersion)
           (com.openai.models.embeddings CreateEmbeddingResponse
                                         CreateEmbeddingResponse$Usage
                                         Embedding
                                         EmbeddingCreateParams
                                         EmbeddingCreateParams$Builder
                                         EmbeddingCreateParams$Input)
           (com.openai.models.models Model
                                      ModelListPage)
           (com.openai.models.responses EasyInputMessage
                                         EasyInputMessage$Builder
                                         EasyInputMessage$Role
                                         FileSearchTool
                                         FileSearchTool$Builder
                                         FileSearchTool$Filters
                                         FileSearchTool$RankingOptions
                                         FileSearchTool$RankingOptions$Builder
                                         FileSearchTool$RankingOptions$Ranker
                                         FunctionTool
                                         FunctionTool$Builder
                                         FunctionTool$Parameters
                                         FunctionTool$Parameters$Builder
                                         Response
                                         Response$IncompleteDetails
                                         Response$IncompleteDetails$Reason
                                         ResponseCreateParams
                                         ResponseCreateParams$Builder
                                         ResponseCreateParams$Input
                                         ResponseCreateParams$Metadata
                                         ResponseCreateParams$Metadata$Builder
                                         ResponseCreateParams$ServiceTier
                                         ResponseCreateParams$ToolChoice
                                         ResponseCreateParams$Truncation
                                         ResponseCreateParams$StreamOptions
                                         ResponseCreateParams$StreamOptions$Builder
                                         ResponseCreateParams$Moderation
                                         ResponseCreateParams$Moderation$Builder
                                         ResponseCompletedEvent
                                         ResponseError
                                         ResponseErrorEvent
                                         ResponseFailedEvent
                                         ResponseFunctionCallArgumentsDeltaEvent
                                         ResponseFunctionCallArgumentsDoneEvent
                                         ResponseFunctionToolCall
                                         ResponseFunctionWebSearch
                                         ResponseIncompleteEvent
                                         ResponseIncludable
                                         ResponseFormatTextJsonSchemaConfig
                                         ResponseFormatTextJsonSchemaConfig$Builder
                                         ResponseFormatTextJsonSchemaConfig$Schema
                                         ResponseFormatTextJsonSchemaConfig$Schema$Builder
                                         ResponseInputContent
                                         ResponseInputFile
                                         ResponseInputFile$Builder
                                         ResponseInputImage
                                         ResponseInputImage$Builder
                                         ResponseInputImage$Detail
                                         ResponseInputMessageItem
                                         ResponseInputMessageItem$Status
                                         ResponseInputItem
                                         ResponseInputItem$FunctionCallOutput
                                         ResponseInputItem$FunctionCallOutput$Builder
                                         ResponseInputText
                                         ResponseInputText$Builder
                                         ResponseItem
                                         ResponseOutputItemAddedEvent
                                         ResponseOutputItemDoneEvent
                                         ResponseOutputItem
                                         ResponseOutputItem$ImageGenerationCall
                                         ResponseOutputItem$LocalShellCall
                                         ResponseOutputItem$McpApprovalRequest
                                         ResponseOutputItem$McpCall
                                         ResponseOutputItem$McpCall$Status
                                         ResponseOutputItem$McpListTools
                                         ResponseOutputMessage
                                         ResponseOutputMessage$Content
                                         ResponseOutputRefusal
                                         ResponseOutputText
                                         ResponseOutputText$Annotation
                                         ResponseOutputText$Annotation$ContainerFileCitation
                                         ResponseOutputText$Annotation$FileCitation
                                         ResponseOutputText$Annotation$FilePath
                                         ResponseOutputText$Annotation$UrlCitation
                                         ResponseOutputText$Logprob
                                         ResponseOutputText$Logprob$TopLogprob
                                         ResponseReasoningItem
                                         ResponseReasoningItem$Status
                                         ResponseReasoningItem$Summary
                                         ResponseReasoningTextDeltaEvent
                                         ResponseReasoningTextDoneEvent
                                         ResponseRefusalDeltaEvent
                                         ResponseRefusalDoneEvent
                                         ResponseStatus
                                         ResponseStreamEvent
                                         ResponseTextConfig
                                         ResponseTextConfig$Builder
                                         ResponseTextConfig$Verbosity
                                         ResponseTextDeltaEvent
                                         ResponseTextDoneEvent
                                         ResponseUsage
                                         Tool
                                         Tool$CodeInterpreter
                                         Tool$CodeInterpreter$Builder
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto
                                         Tool$CodeInterpreter$Container$CodeInterpreterToolAuto$Builder
                                         Tool$Mcp
                                         Tool$Mcp$Builder
                                         Tool$Mcp$Headers
                                         Tool$Mcp$Headers$Builder
                                         Tool$Mcp$RequireApproval$McpToolApprovalSetting
                                         ToolChoiceFunction
                                         ToolChoiceFunction$Builder
                                         ToolChoiceOptions
                                         WebSearchTool
                                         WebSearchTool$Builder
                                         WebSearchTool$Filters
                                         WebSearchTool$Filters$Builder
                                         WebSearchTool$SearchContextSize
                                         WebSearchTool$UserLocation
                                         WebSearchTool$UserLocation$Builder
                                         WebSearchTool$Type)
           (com.openai.models.responses.inputitems InputItemListPage)
           (com.openai.models.responses.inputtokens InputTokenCountParams
                                                      InputTokenCountParams$Builder
                                                      InputTokenCountParams$Truncation
                                                      InputTokenCountResponse)
           (com.openai.services.blocking BatchService
                                         EmbeddingService
                                         FileService
                                         ModelService
                                         ResponseService)
           (com.openai.services.blocking.responses InputItemService
                                                    InputTokenService)))

(set! *warn-on-reflection* true)

(defn client
  "An OpenAI client. With no args, resolves credentials from the environment
  (`OPENAI_API_KEY`). Pass explicit config keys to set client options:
  `:api-key`, `:organization`, `:project`, `:base-url`, `:timeout-ms`,
  `:max-retries`, and `:azure-service-version` (an Azure OpenAI api-version
  string, used together with an Azure `:base-url`)."
  (^OpenAIClient [] (OpenAIOkHttpClient/fromEnv))
  (^OpenAIClient [{:keys [api-key organization project base-url timeout-ms max-retries
                          azure-service-version]}]
   (let [^OpenAIOkHttpClient$Builder b (OpenAIOkHttpClient/builder)]
     (when api-key (.apiKey b ^String api-key))
     (when organization (.organization b ^String organization))
     (when project (.project b ^String project))
     (when base-url (.baseUrl b ^String base-url))
     (when timeout-ms (.timeout b (Duration/ofMillis (long timeout-ms))))
     (when max-retries (.maxRetries b (int max-retries)))
     (when azure-service-version
       (.azureServiceVersion b (AzureOpenAIServiceVersion/fromString ^String azure-service-version)))
     (.build b))))

(defn- service-error-type [e]
  (condp instance? e
    BadRequestException :bad-request
    UnauthorizedException :unauthorized
    PermissionDeniedException :permission-denied
    NotFoundException :not-found
    UnprocessableEntityException :unprocessable-entity
    RateLimitException :rate-limit
    InternalServerException :internal-server
    UnexpectedStatusCodeException :unexpected-status
    :api-error))

(defn- throw-normalized!
  "Rethrow an SDK exception: service errors and I/O errors become ex-info
  keyed `:openai/error` with the original as cause; anything else propagates
  unchanged."
  [^Throwable e]
  (cond
    (instance? OpenAIServiceException e)
    (throw (ex-info (or (.getMessage e) "OpenAI API error")
                    {:openai/error :api-error
                     :status (.statusCode ^OpenAIServiceException e)
                     :error-type (service-error-type e)}
                    e))
    (instance? OpenAIIoException e)
    (throw (ex-info (or (.getMessage e) "OpenAI I/O error")
                    {:openai/error :io-error}
                    e))
    :else (throw e)))

(defmacro ^:private with-api-errors [& body]
  `(try ~@body
        (catch OpenAIException e# (throw-normalized! e#))))

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

(defn- enum-name [k]
  (str/replace (name k) "-" "_"))

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

(defn- ->input-text ^ResponseInputContent [{:keys [text]}]
  (let [^ResponseInputText$Builder b (ResponseInputText/builder)]
    (when-not text (missing-key! :text))
    (.text b ^String text)
    (ResponseInputContent/ofInputText (.build b))))

(defn- ->input-image ^ResponseInputContent [{:keys [image-url file-id detail]}]
  (let [^ResponseInputImage$Builder b (ResponseInputImage/builder)]
    (when image-url (.imageUrl b ^String image-url))
    (when file-id (.fileId b ^String file-id))
    (when detail (.detail b (ResponseInputImage$Detail/of (enum-name detail))))
    (ResponseInputContent/ofInputImage (.build b))))

(defn- ->input-file ^ResponseInputContent [{:keys [file-id filename file-data]}]
  (let [^ResponseInputFile$Builder b (ResponseInputFile/builder)]
    (when file-id (.fileId b ^String file-id))
    (when filename (.filename b ^String filename))
    (when file-data (.fileData b ^String file-data))
    (ResponseInputContent/ofInputFile (.build b))))

(defn- ->input-content ^ResponseInputContent [{:keys [type] :as part}]
  (case (keyword type)
    :text (->input-text part)
    :image (->input-image part)
    :file (->input-file part)
    (throw (ex-info (str "Unknown content type " type)
                    {:openai/error :unknown-content-type :type type}))))

(defn- ->input-message ^ResponseInputItem [{:keys [role content type] :as item}]
  (if (= :function-call-output (keyword type))
    (->function-call-output item)
    (let [^EasyInputMessage$Builder b (EasyInputMessage/builder)]
      (when-not role (missing-key! :role))
      (when-not content (missing-key! :content))
      (.role b (->role role))
      (if (string? content)
        (.content b ^String content)
        (.contentOfResponseInputMessageContentList
         b ^java.util.List (mapv ->input-content content)))
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

(defn- ->json-schema-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn- ->json-value-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn- ->text-config ^ResponseTextConfig [json-schema verbosity]
  (let [^ResponseTextConfig$Builder tb (ResponseTextConfig/builder)]
    (when json-schema
      (let [{:keys [name schema strict description]} json-schema
            ^ResponseFormatTextJsonSchemaConfig$Schema$Builder sb
            (ResponseFormatTextJsonSchemaConfig$Schema/builder)
            ^ResponseFormatTextJsonSchemaConfig$Builder fb
            (ResponseFormatTextJsonSchemaConfig/builder)]
        (when-not name (missing-key! :name))
        (when-not schema (missing-key! :schema))
        (.additionalProperties sb ^java.util.Map (->json-schema-properties schema))
        (.name fb ^String name)
        (.schema fb (.build sb))
        (when description (.description fb ^String description))
        (when (some? strict) (.strict fb (boolean strict)))
        (.format tb (.build fb))))
    (when verbosity
      (.verbosity tb (ResponseTextConfig$Verbosity/of (name verbosity))))
    (.build tb)))

(defn- ->stream-options ^ResponseCreateParams$StreamOptions [{:keys [include-obfuscation]}]
  (let [^ResponseCreateParams$StreamOptions$Builder b (ResponseCreateParams$StreamOptions/builder)]
    (when (some? include-obfuscation)
      (.includeObfuscation b (boolean include-obfuscation)))
    (.build b)))

(defn- ->moderation ^ResponseCreateParams$Moderation [{:keys [model]}]
  (let [^ResponseCreateParams$Moderation$Builder b (ResponseCreateParams$Moderation/builder)]
    (when model (.model b ^String model))
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

(defn- ->web-search-filters ^WebSearchTool$Filters [{:keys [allowed-domains]}]
  (let [^WebSearchTool$Filters$Builder b (WebSearchTool$Filters/builder)]
    (when (seq allowed-domains)
      (.allowedDomains b ^java.util.List (vec allowed-domains)))
    (.build b)))

(defn- ->web-search-user-location ^WebSearchTool$UserLocation
  [{:keys [city country region timezone]}]
  (let [^WebSearchTool$UserLocation$Builder b (WebSearchTool$UserLocation/builder)]
    (when city (.city b ^String city))
    (when country (.country b ^String country))
    (when region (.region b ^String region))
    (when timezone (.timezone b ^String timezone))
    (.build b)))

(defn- ->web-search-tool ^WebSearchTool
  [{:keys [search-context-size user-location allowed-domains]}]
  (let [^WebSearchTool$Builder b (WebSearchTool/builder)]
    (.type b WebSearchTool$Type/WEB_SEARCH)
    (when search-context-size
      (.searchContextSize b (WebSearchTool$SearchContextSize/of (name search-context-size))))
    (when user-location (.userLocation b (->web-search-user-location user-location)))
    (when (seq allowed-domains) (.filters b (->web-search-filters {:allowed-domains allowed-domains})))
    (.build b)))

(defn- ->comparison-filter ^ComparisonFilter [{:keys [type key value]}]
  (let [^ComparisonFilter$Builder b (ComparisonFilter/builder)]
    (when-not type (missing-key! :type))
    (when-not key (missing-key! :key))
    (.type b (ComparisonFilter$Type/of (name type)))
    (.key b ^String key)
    (cond
      (string? value) (.value b ^String value)
      (number? value) (.value b (double value))
      (instance? Boolean value) (.value b (boolean value))
      :else (.value b ^String (str value)))
    (.build b)))

(defn- filter->plain
  "A filter map as plain JSON-shaped data, for nesting a compound filter
  inside another compound filter (the SDK models only one level natively)."
  [{:keys [type key value filters]}]
  (if filters
    {"type" (name type) "filters" (mapv filter->plain filters)}
    {"type" (name type) "key" key "value" value}))

(defn- ->compound-filter ^CompoundFilter [{:keys [type filters]}]
  (when-not type (missing-key! :type))
  (let [^CompoundFilter$Builder b (CompoundFilter/builder)]
    (.type b (CompoundFilter$Type/of (name type)))
    (doseq [f filters]
      (if (:filters f)
        (.addFilter b (JsonValue/from (filter->plain f)))
        (.addFilter b (->comparison-filter f))))
    (.build b)))

(defn- ->file-search-filters ^FileSearchTool$Filters [filters]
  (if (:filters filters)
    (FileSearchTool$Filters/ofCompoundFilter (->compound-filter filters))
    (FileSearchTool$Filters/ofComparisonFilter (->comparison-filter filters))))

(defn- ->ranking-options ^FileSearchTool$RankingOptions [{:keys [ranker score-threshold]}]
  (let [^FileSearchTool$RankingOptions$Builder b (FileSearchTool$RankingOptions/builder)]
    (when ranker (.ranker b (FileSearchTool$RankingOptions$Ranker/of (str ranker))))
    (when (some? score-threshold) (.scoreThreshold b (double score-threshold)))
    (.build b)))

(defn- ->file-search-tool ^FileSearchTool
  [{:keys [vector-store-ids max-num-results filters ranking-options]}]
  (let [^FileSearchTool$Builder b (FileSearchTool/builder)]
    (when-not (seq vector-store-ids) (missing-key! :vector-store-ids))
    (.vectorStoreIds b ^java.util.List (vec vector-store-ids))
    (when max-num-results (.maxNumResults b (long max-num-results)))
    (when filters (.filters b (->file-search-filters filters)))
    (when ranking-options (.rankingOptions b (->ranking-options ranking-options)))
    (.build b)))

(defn- ->mcp-headers ^Tool$Mcp$Headers [headers]
  (let [^Tool$Mcp$Headers$Builder b (Tool$Mcp$Headers/builder)]
    (.additionalProperties b ^java.util.Map (->json-value-properties headers))
    (.build b)))

(defn- ->mcp-tool ^Tool$Mcp
  [{:keys [server-label server-url allowed-tools require-approval headers]}]
  (let [^Tool$Mcp$Builder b (Tool$Mcp/builder)]
    (when-not server-label (missing-key! :server-label))
    (.serverLabel b ^String server-label)
    (when server-url (.serverUrl b ^String server-url))
    (when (seq allowed-tools) (.allowedToolsOfMcp b ^java.util.List (vec allowed-tools)))
    (when require-approval
      (.requireApproval b (Tool$Mcp$RequireApproval$McpToolApprovalSetting/of (name require-approval))))
    (when headers (.headers b (->mcp-headers headers)))
    (.build b)))

(defn- ->tool ^Tool [{:keys [type] :as tool}]
  (case (keyword type)
    :function (Tool/ofFunction (->function-tool tool))
    :web-search (Tool/ofWebSearch (->web-search-tool tool))
    :file-search (Tool/ofFileSearch (->file-search-tool tool))
    :mcp (Tool/ofMcp (->mcp-tool tool))
    :code-interpreter (Tool/ofCodeInterpreter (->code-interpreter tool))
    (throw (ex-info (str "Unknown tool type " type)
                    {:openai/error :unknown-tool-type :type type}))))

(defn- ->tool-choice-function ^ToolChoiceFunction [{:keys [name]}]
  (let [^ToolChoiceFunction$Builder b (ToolChoiceFunction/builder)]
    (when-not name (missing-key! :name))
    (.name b ^String name)
    (.build b)))

(defn- ->tool-choice-option ^ToolChoiceOptions [choice]
  (case (keyword choice)
    :auto ToolChoiceOptions/AUTO
    :required ToolChoiceOptions/REQUIRED
    :none ToolChoiceOptions/NONE
    (throw (ex-info (str "Unknown tool choice " choice)
                    {:openai/error :unknown-tool-choice
                     :tool-choice choice}))))

(defn- ->tool-choice ^ResponseCreateParams$ToolChoice [choice]
  (if (map? choice)
    (case (keyword (:type choice))
      :function (ResponseCreateParams$ToolChoice/ofFunction
                 (->tool-choice-function choice))
      (throw (ex-info (str "Unknown tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type
                       :type (:type choice)})))
    (ResponseCreateParams$ToolChoice/ofOptions (->tool-choice-option choice))))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p
           metadata previous-response-id store reasoning user tools tool-choice
           parallel-tool-calls background include truncation prompt-cache-key
           safety-identifier service-tier max-tool-calls top-logprobs
           json-schema verbosity conversation stream-options moderation]}]
  (when-not model (missing-key! :model))
  (when-not input (missing-key! :input))
  (let [^ResponseCreateParams$Builder b (ResponseCreateParams/builder)]
    (.model b ^String model)
    (.input b (->input input))
    (when instructions (.instructions b ^String instructions))
    (when max-output-tokens (.maxOutputTokens b (long max-output-tokens)))
    (when max-tool-calls (.maxToolCalls b (long max-tool-calls)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when top-logprobs (.topLogprobs b (long top-logprobs)))
    (when (some? background) (.background b (boolean background)))
    (doseq [i include] (.addInclude b (ResponseIncludable/of (enum-name i))))
    (when truncation (.truncation b (ResponseCreateParams$Truncation/of (enum-name truncation))))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (when service-tier (.serviceTier b (ResponseCreateParams$ServiceTier/of (enum-name service-tier))))
    (when metadata (.metadata b (->metadata metadata)))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? store) (.store b (boolean store)))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (when user (.user b ^String user))
    (doseq [t tools] (.addTool b (->tool t)))
    (when tool-choice (.toolChoice b (->tool-choice tool-choice)))
    (when (some? parallel-tool-calls)
      (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when (or json-schema verbosity)
      (.text b (->text-config json-schema verbosity)))
    (when conversation (.conversation b ^String conversation))
    (when stream-options (.streamOptions b (->stream-options stream-options)))
    (when moderation (.moderation b (->moderation moderation)))
    (.build b)))

(defn- ->input-token-count-params ^InputTokenCountParams
  [{:keys [model input instructions previous-response-id reasoning tools tool-choice
           parallel-tool-calls truncation]}]
  (let [^InputTokenCountParams$Builder b (InputTokenCountParams/builder)]
    (when model (.model b ^String model))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfResponseInputItems b ^java.util.List (mapv ->input-message input))))
    (when instructions (.instructions b ^String instructions))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (doseq [t tools] (.addTool b (->tool t)))
    (when tool-choice
      (if (map? tool-choice)
        (case (keyword (:type tool-choice))
          :function (.toolChoice b (->tool-choice-function tool-choice))
          (throw (ex-info (str "Unknown tool choice type " (:type tool-choice))
                          {:openai/error :unknown-tool-choice-type
                           :type (:type tool-choice)})))
        (.toolChoice b (->tool-choice-option tool-choice))))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when truncation (.truncation b (InputTokenCountParams$Truncation/of (enum-name truncation))))
    (.build b)))

(defn- ->keyword [s]
  (-> s str str/lower-case (str/replace "_" "-") keyword))

(defn- parse-arguments [^String s]
  (try
    (json/read-value s json-mapper)
    (catch Exception _
      s)))

(defn- opt-get [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- annotation->map [^ResponseOutputText$Annotation a]
  (cond
    (.isUrlCitation a) (let [^ResponseOutputText$Annotation$UrlCitation u (.asUrlCitation a)]
                         {:type :url-citation
                          :url (.url u)
                          :title (.title u)
                          :start-index (.startIndex u)
                          :end-index (.endIndex u)})
    (.isFileCitation a) (let [^ResponseOutputText$Annotation$FileCitation f (.asFileCitation a)]
                          {:type :file-citation
                           :file-id (.fileId f)
                           :filename (.filename f)
                           :index (.index f)})
    (.isContainerFileCitation a) (let [^ResponseOutputText$Annotation$ContainerFileCitation f
                                       (.asContainerFileCitation a)]
                                   {:type :container-file-citation
                                    :container-id (.containerId f)
                                    :file-id (.fileId f)
                                    :filename (.filename f)
                                    :start-index (.startIndex f)
                                    :end-index (.endIndex f)})
    (.isFilePath a) (let [^ResponseOutputText$Annotation$FilePath f (.asFilePath a)]
                      {:type :file-path
                       :file-id (.fileId f)
                       :index (.index f)})
    :else {:type :unknown}))

(defn- top-logprob->map [^ResponseOutputText$Logprob$TopLogprob l]
  {:token (.token l)
   :bytes (vec (.bytes l))
   :logprob (.logprob l)})

(defn- logprob->map [^ResponseOutputText$Logprob l]
  {:token (.token l)
   :bytes (vec (.bytes l))
   :logprob (.logprob l)
   :top-logprobs (mapv top-logprob->map (.topLogprobs l))})

(defn- content->map [^ResponseOutputMessage$Content c]
  (cond
    (.isOutputText c) (let [^ResponseOutputText t (.asOutputText c)]
                        (cond-> {:type :text :text (.text t)}
                          (seq (.annotations t)) (assoc :annotations (mapv annotation->map (.annotations t)))
                          (.isPresent (.logprobs t)) (assoc :logprobs (mapv logprob->map (.get (.logprobs t))))))
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
  (cond-> {:type :reasoning
           :id (.id r)}
    (.isPresent (.status r)) (assoc :status (->keyword (.asString ^ResponseReasoningItem$Status (.get (.status r)))))
    (seq (.summary r)) (assoc :summary (mapv #(.text ^ResponseReasoningItem$Summary %) (.summary r)))))

(defn- web-search-call->map [^ResponseFunctionWebSearch c]
  {:type :web-search-call :status (->keyword (.asString (.status c)))})

(defn- file-search-call->map [^com.openai.models.responses.ResponseFileSearchToolCall c]
  {:type :file-search-call :status (->keyword (.asString (.status c)))})

(defn- code-interpreter-call->map [^com.openai.models.responses.ResponseCodeInterpreterToolCall c]
  {:type :code-interpreter-call :status (->keyword (.asString (.status c)))})

(defn- image-generation-call->map [^ResponseOutputItem$ImageGenerationCall c]
  (cond-> {:type :image-generation-call
           :id (.id c)
           :status (->keyword (.asString (.status c)))}
    (.isPresent (.result c)) (assoc :result (.get (.result c)))))

(defn- mcp-call->map [^ResponseOutputItem$McpCall c]
  (cond-> {:type :mcp-call
           :id (.id c)
           :name (.name c)
           :server-label (.serverLabel c)
    :arguments (.arguments c)}
    (.isPresent (.status c)) (assoc :status (->keyword (.asString ^ResponseOutputItem$McpCall$Status (.get (.status c)))))
    (.isPresent (.output c)) (assoc :output (.get (.output c)))
    (.isPresent (.error c)) (assoc :error (.get (.error c)))))

(defn- mcp-list-tools->map [^ResponseOutputItem$McpListTools c]
  (cond-> {:type :mcp-list-tools
           :id (.id c)
           :server-label (.serverLabel c)}
    (.isPresent (.error c)) (assoc :error (.get (.error c)))))

(defn- mcp-approval-request->map [^ResponseOutputItem$McpApprovalRequest c]
  {:type :mcp-approval-request
   :id (.id c)
   :name (.name c)
   :server-label (.serverLabel c)
   :arguments (.arguments c)})

(defn- custom-tool-call->map [^com.openai.models.responses.ResponseCustomToolCall c]
  (cond-> {:type :custom-tool-call
           :name (.name c)
           :input (.input c)
           :call-id (.callId c)}
    (.isPresent (.id c)) (assoc :id (.get (.id c)))))

(defn- local-shell-call->map [^ResponseOutputItem$LocalShellCall c]
  {:type :local-shell-call
   :id (.id c)
   :status (->keyword (.asString (.status c)))
   :call-id (.callId c)})

(defn- computer-call->map [^com.openai.models.responses.ResponseComputerToolCall c]
  {:type :computer-call
   :id (.id c)
   :status (->keyword (.asString (.status c)))})

(defn- output-item->map [^ResponseOutputItem item]
  (cond
    (.isMessage item) (message->map (.asMessage item))
    (.isFunctionCall item) (function-call->map (.asFunctionCall item))
    (.isReasoning item) (reasoning->map (.asReasoning item))
    (.isWebSearchCall item) (web-search-call->map (.asWebSearchCall item))
    (.isFileSearchCall item) (file-search-call->map (.asFileSearchCall item))
    (.isCodeInterpreterCall item) (code-interpreter-call->map (.asCodeInterpreterCall item))
    (.isImageGenerationCall item) (image-generation-call->map (.asImageGenerationCall item))
    (.isMcpCall item) (mcp-call->map (.asMcpCall item))
    (.isMcpListTools item) (mcp-list-tools->map (.asMcpListTools item))
    (.isMcpApprovalRequest item) (mcp-approval-request->map (.asMcpApprovalRequest item))
    (.isCustomToolCall item) (custom-tool-call->map (.asCustomToolCall item))
    (.isLocalShellCall item) (local-shell-call->map (.asLocalShellCall item))
    (.isComputerCall item) (computer-call->map (.asComputerCall item))
    :else {:type :unknown}))

(defn- input-message-item->map [^ResponseInputMessageItem m]
  (cond-> {:type :message
           :role (->keyword (.asString (.role m)))
           :id (.id m)}
    (.isPresent (.status m)) (assoc :status (->keyword (.asString ^com.openai.models.responses.ResponseInputMessageItem$Status (.get (.status m)))))))

(defn- response-item->map [^ResponseItem item]
  (cond
    (.isResponseInputMessageItem item) (input-message-item->map (.asResponseInputMessageItem item))
    (.isResponseOutputMessage item) (message->map (.asResponseOutputMessage item))
    (.isFunctionCall item) (function-call->map (.toResponseFunctionToolCall (.asFunctionCall item)))
    (.isFunctionCallOutput item) (let [c (.asFunctionCallOutput item)]
                                   {:type :function-call-output
                                    :id (.id c)
                                    :call-id (.callId c)
                                    :status (->keyword (.asString (.status c)))
                                    :output (str (.output c))})
    :else {:type :unknown}))

(defn- usage->map [^ResponseUsage u]
  {:input-tokens (.inputTokens u)
   :output-tokens (.outputTokens u)
   :total-tokens (.totalTokens u)})

(defn- error->map [^ResponseError e]
  {:code (->keyword (.asString (.code e)))
   :message (.message e)})

(defn- incomplete-details->map [^Response$IncompleteDetails d]
  (cond-> {}
    (.isPresent (.reason d)) (assoc :reason (->keyword (.asString ^com.openai.models.responses.Response$IncompleteDetails$Reason (.get (.reason d)))))))

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
      (.isPresent (.incompleteDetails r)) (assoc :incomplete-details (incomplete-details->map (.get (.incompleteDetails r))))
      (.isPresent (.previousResponseId r)) (assoc :previous-response-id (.get (.previousResponseId r))))))

(defn create-response
  "Send a Responses API request and return a Clojure map.

  Request keys: `:model` (required string), `:input` (required string or vector),
  `:instructions`, `:max-output-tokens`, `:temperature`, `:top-p`, `:metadata`,
  `:previous-response-id`, `:store`, `:reasoning`, `:user`, `:tools`,
  `:tool-choice`, `:parallel-tool-calls`, `:background`, `:include`,
  `:truncation`, `:prompt-cache-key`, `:safety-identifier`, `:service-tier`,
  `:max-tool-calls`, `:top-logprobs`, `:json-schema`, `:verbosity`,
  `:conversation`, `:stream-options`, and `:moderation`.

  Message-vector input items accept `{:role :system|:developer|:user|:assistant
  :content \"...\"}`, multimodal content vectors containing text, image, or file
  part maps, and `{:type :function-call-output :call-id \"...\" :output \"...\"}`.
  Map outputs are JSON-encoded.

  Structured outputs: `:json-schema {:name \"...\" :schema {...} :strict true
  :description \"...\"}`.

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
  (with-api-errors
    (response->map (.create (.responses client) (->params req)))))

(defn count-input-tokens
  "Count input tokens for a Responses request shape. Accepts the same request map
  style as `create-response`; fields unsupported by the SDK's input token count
  endpoint are ignored. Returns `{:input-tokens n}`."
  [^OpenAIClient client req]
  (with-api-errors
    (let [^ResponseService svc (.responses client)
          ^InputTokenService tokens (.inputTokens svc)
          ^InputTokenCountResponse r (.count tokens (->input-token-count-params req))]
      {:input-tokens (.inputTokens r)})))

(defn- model->map [^Model m]
  {:id (.id m)
   :created (.created m)
   :owned-by (.ownedBy m)})

(defn- ->embedding-params ^EmbeddingCreateParams
  [{:keys [model input dimensions user]}]
  (when-not model (missing-key! :model))
  (when-not input (missing-key! :input))
  (let [^EmbeddingCreateParams$Builder b (EmbeddingCreateParams/builder)]
    (.model b ^String model)
    (if (string? input)
      (.input b (EmbeddingCreateParams$Input/ofString input))
      (.input b (EmbeddingCreateParams$Input/ofArrayOfStrings ^java.util.List (vec input))))
    (when dimensions (.dimensions b (long dimensions)))
    (when user (.user b ^String user))
    (.build b)))

(defn- embedding-response->map [^CreateEmbeddingResponse r]
  (let [^CreateEmbeddingResponse$Usage u (.usage r)]
    {:model (.model r)
     :embeddings (->> (.data r)
                      (sort-by (fn [^Embedding e] (.index e)))
                      (mapv (fn [^Embedding e] (vec (.embedding e)))))
     :usage {:prompt-tokens (.promptTokens u)
             :total-tokens (.totalTokens u)}}))

(defn create-embeddings
  "Create embeddings for `:input` (a string, or a vector of strings) with
  `:model` (required, e.g. \"text-embedding-3-small\"). Optional: `:dimensions`
  (truncated output size, supported by v3 models) and `:user`.

  Returns `{:model \"...\" :embeddings [[floats ...] ...] :usage
  {:prompt-tokens n :total-tokens n}}`; `:embeddings` is ordered to match the
  input order (one vector even for string input)."
  [^OpenAIClient client req]
  (with-api-errors
    (let [^EmbeddingService svc (.embeddings client)]
      (embedding-response->map (.create svc (->embedding-params req))))))

(defn list-models
  "List available models as a vector of `{:id :created :owned-by}` maps. Pages
  are followed automatically."
  [^OpenAIClient client]
  (with-api-errors
    (let [^ModelService svc (.models client)
          ^ModelListPage p (.list svc)]
      (mapv model->map (.autoPager p)))))

(defn get-model
  "Retrieve one model by id as a `{:id :created :owned-by}` map."
  [^OpenAIClient client ^String model-id]
  (with-api-errors
    (let [^ModelService svc (.models client)]
      (model->map (.retrieve svc model-id)))))

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

(defn- drain-stream
  ^String [^StreamResponse sr on-event]
  (let [sb (StringBuilder.)]
    (doseq [ev (iterator-seq (.iterator (.stream sr)))]
      (let [m (event->map ev)]
        (when (= :output-text-delta (:type m))
          (.append sb ^String (:delta m)))
        (when on-event (on-event m))))
    (str sb)))

(defn stream
  "Stream a Responses API request, invoking `on-event` with a normalized event
  map for every server-sent event as it arrives, and returning the concatenated
  output text. Takes the same `req` map as `create-response`. The underlying
  HTTP stream is closed automatically."
  ^String [^OpenAIClient client req on-event]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (with-open [^StreamResponse sr (.createStreaming svc (->params req))]
        (drain-stream sr on-event)))))

(defn retrieve-streaming
  "Resume streaming an existing background response id. Invokes `on-event` with
  normalized event maps and returns concatenated output text, matching `stream`."
  ^String [^OpenAIClient client ^String response-id on-event]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (with-open [^StreamResponse sr (.retrieveStreaming svc response-id)]
        (drain-stream sr on-event)))))

(defn stream-text
  "Stream a Responses API request, calling `on-text` with each output text delta
  string as it arrives, and returning the full concatenated text."
  ^String [^OpenAIClient client req on-text]
  (stream client req
          (fn [m] (when (and on-text (= :output-text-delta (:type m))) (on-text (:delta m))))))

(defn get-response
  "Retrieve one stored response by id as a response map."
  [^OpenAIClient client ^String response-id]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (response->map (.retrieve svc response-id)))))

(defn list-input-items
  "List input items for a stored response id as normalized maps. Pages are
  followed automatically."
  [^OpenAIClient client ^String response-id]
  (with-api-errors
    (let [^ResponseService svc (.responses client)
          ^InputItemService items (.inputItems svc)
          ^InputItemListPage p (.list items response-id)]
      (mapv response-item->map (.autoPager p)))))

(defn delete-response
  "Delete one stored response by id. The OpenAI Java SDK returns void."
  [^OpenAIClient client ^String response-id]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (.delete svc response-id))
    nil))

(defn cancel-response
  "Cancel an in-progress response by id and return the resulting response map."
  [^OpenAIClient client ^String response-id]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (response->map (.cancel svc response-id)))))

(defn- compacted-response->map [^com.openai.models.responses.CompactedResponse r]
  (let [items (mapv output-item->map (.output r))]
    {:id (.id r)
     :output items
     :text (output-text items)
     :usage (usage->map (.usage r))
     :created-at (.createdAt r)}))

(defn compact
  "Compact a previous response by id and return the compacted response map."
  [^OpenAIClient client ^String response-id]
  (with-api-errors
    (let [^ResponseService svc (.responses client)]
      (compacted-response->map
       (.compact svc (-> (com.openai.models.responses.ResponseCompactParams/builder)
                         (.previousResponseId response-id)
                         (.build)))))))

;; Files

(defn- ->file-purpose ^FilePurpose [purpose]
  (FilePurpose/of (enum-name purpose)))

(defn- ->file-expires-after ^FileCreateParams$ExpiresAfter [{:keys [seconds]}]
  (when-not seconds (missing-key! :seconds))
  (let [^FileCreateParams$ExpiresAfter$Builder b (FileCreateParams$ExpiresAfter/builder)]
    (.anchor b (JsonValue/from "created_at"))
    (.seconds b (long seconds))
    (.build b)))

(defn- ->file-input-stream ^java.io.InputStream [file]
  (cond
    (instance? java.io.InputStream file) file
    (bytes? file) (java.io.ByteArrayInputStream. ^bytes file)
    :else (throw (ex-info (str "Unsupported :file type " (class file))
                          {:openai/error :unsupported-file-type :class (class file)}))))

(defn- ->file-create-params ^FileCreateParams
  [{:keys [file purpose filename expires-after]}]
  (when-not file (missing-key! :file))
  (when-not purpose (missing-key! :purpose))
  (let [^FileCreateParams$Builder b (FileCreateParams/builder)]
    (cond
      (instance? java.nio.file.Path file) (.file b ^java.nio.file.Path file)
      (string? file) (.file b (.toPath (java.io.File. ^String file)))
      filename (.file b (-> (MultipartField/builder)
                            (.value (->file-input-stream file))
                            (.filename ^String filename)
                            (.build)))
      :else (.file b (->file-input-stream file)))
    (.purpose b (->file-purpose purpose))
    (when expires-after (.expiresAfter b (->file-expires-after expires-after)))
    (.build b)))

(defn- file->map [^FileObject f]
  (cond-> {:id (.id f)
           :bytes (.bytes f)
           :created-at (.createdAt f)
           :filename (.filename f)
           :purpose (->keyword (.asString (.purpose f)))
           :status (->keyword (.asString (.status f)))}
    (.isPresent (.expiresAt f)) (assoc :expires-at (.get (.expiresAt f)))
    (.isPresent (.statusDetails f)) (assoc :status-details (.get (.statusDetails f)))))

(defn- ->file-list-params ^FileListParams [{:keys [purpose order after limit]}]
  (let [^FileListParams$Builder b (FileListParams/builder)]
    (when purpose (.purpose b ^String (name purpose)))
    (when order (.order b (FileListParams$Order/of (name order))))
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn upload-file
  "Upload a file. `:file` (required) is a `java.nio.file.Path`, a string path,
  a byte array, or an `InputStream`; `:purpose` (required) is e.g. `:batch`,
  `:assistants`, `:fine-tune`, `:vision`, `:user-data`, or `:evals`.
  Optional: `:filename` (used with byte-array/stream input) and
  `:expires-after {:seconds n}` (anchored to file creation time).

  Returns `{:id :bytes :created-at :filename :purpose :status}` plus
  `:expires-at`/`:status-details` when present."
  [^OpenAIClient client req]
  (with-api-errors
    (let [^FileService svc (.files client)]
      (file->map (.create svc (->file-create-params req))))))

(defn get-file
  "Retrieve one file's metadata by id as a file map."
  [^OpenAIClient client ^String file-id]
  (with-api-errors
    (let [^FileService svc (.files client)]
      (file->map (.retrieve svc file-id)))))

(defn file-content
  "Download a file's content by id and return it as a byte array."
  ^bytes [^OpenAIClient client ^String file-id]
  (with-api-errors
    (let [^FileService svc (.files client)]
      (with-open [r (.content svc file-id)]
        (.readAllBytes (.body r))))))

(defn list-files
  "List files as a vector of file maps. Optional keys: `:purpose`, `:order`
  (`:asc`/`:desc`), `:after`, and `:limit`. Pages are followed automatically."
  ([^OpenAIClient client] (list-files client {}))
  ([^OpenAIClient client opts]
   (with-api-errors
     (let [^FileService svc (.files client)
           ^FileListPage p (.list svc (->file-list-params opts))]
       (mapv file->map (.autoPager p))))))

(defn delete-file
  "Delete a file by id. Returns `{:id \"...\" :deleted true|false}`."
  [^OpenAIClient client ^String file-id]
  (with-api-errors
    (let [^FileService svc (.files client)
          ^FileDeleted d (.delete svc file-id)]
      {:id (.id d) :deleted (.deleted d)})))

;; Batches

(defn- ->batch-metadata ^BatchCreateParams$Metadata [m]
  (let [^BatchCreateParams$Metadata$Builder b (BatchCreateParams$Metadata/builder)]
    (doseq [[k v] (walk/stringify-keys m)]
      (.putAdditionalProperty b ^String k (JsonValue/from (str v))))
    (.build b)))

(defn- ->output-expires-after ^BatchCreateParams$OutputExpiresAfter [{:keys [seconds]}]
  (when-not seconds (missing-key! :seconds))
  (let [^BatchCreateParams$OutputExpiresAfter$Builder b
        (BatchCreateParams$OutputExpiresAfter/builder)]
    (.anchor b (JsonValue/from "created_at"))
    (.seconds b (long seconds))
    (.build b)))

(defn- ->batch-create-params ^BatchCreateParams
  [{:keys [input-file-id endpoint completion-window metadata output-expires-after]}]
  (when-not input-file-id (missing-key! :input-file-id))
  (when-not endpoint (missing-key! :endpoint))
  (let [^BatchCreateParams$Builder b (BatchCreateParams/builder)]
    (.inputFileId b ^String input-file-id)
    (.endpoint b (BatchCreateParams$Endpoint/of endpoint))
    (.completionWindow b (BatchCreateParams$CompletionWindow/of (or completion-window "24h")))
    (when metadata (.metadata b (->batch-metadata metadata)))
    (when output-expires-after
      (.outputExpiresAfter b (->output-expires-after output-expires-after)))
    (.build b)))

(defn- batch->map [^Batch b]
  (cond-> {:id (.id b)
           :status (->keyword (.asString (.status b)))
           :endpoint (.endpoint b)
           :input-file-id (.inputFileId b)
           :completion-window (.completionWindow b)
           :created-at (.createdAt b)}
    (.isPresent (.outputFileId b)) (assoc :output-file-id (.get (.outputFileId b)))
    (.isPresent (.errorFileId b)) (assoc :error-file-id (.get (.errorFileId b)))
    (.isPresent (.model b)) (assoc :model (.get (.model b)))
    (.isPresent (.completedAt b)) (assoc :completed-at (.get (.completedAt b)))
    (.isPresent (.failedAt b)) (assoc :failed-at (.get (.failedAt b)))
    (.isPresent (.expiresAt b)) (assoc :expires-at (.get (.expiresAt b)))
    (.isPresent (.requestCounts b))
    (assoc :request-counts
           (let [^BatchRequestCounts c (.get (.requestCounts b))]
             {:completed (.completed c) :failed (.failed c) :total (.total c)}))))

(defn- ->batch-list-params ^BatchListParams [{:keys [after limit]}]
  (let [^BatchListParams$Builder b (BatchListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn create-batch
  "Create a batch job. Required: `:input-file-id` (an uploaded `:batch`-purpose
  JSONL file) and `:endpoint` (the API path string the batch targets, e.g.
  \"/v1/responses\", \"/v1/chat/completions\", or \"/v1/embeddings\").
  Optional: `:completion-window` (defaults to \"24h\"), `:metadata`, and
  `:output-expires-after {:seconds n}`.

  Returns `{:id :status :endpoint :input-file-id :completion-window
  :created-at}` plus `:output-file-id`, `:error-file-id`, `:model`,
  `:completed-at`, `:failed-at`, `:expires-at`, or `:request-counts
  {:completed :failed :total}` when present."
  [^OpenAIClient client req]
  (with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.create svc (->batch-create-params req))))))

(defn get-batch
  "Retrieve one batch by id as a batch map."
  [^OpenAIClient client ^String batch-id]
  (with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.retrieve svc batch-id)))))

(defn cancel-batch
  "Cancel an in-progress batch by id and return the resulting batch map."
  [^OpenAIClient client ^String batch-id]
  (with-api-errors
    (let [^BatchService svc (.batches client)]
      (batch->map (.cancel svc batch-id)))))

(defn list-batches
  "List batches as a vector of batch maps. Optional keys: `:after` and
  `:limit`. Pages are followed automatically."
  ([^OpenAIClient client] (list-batches client {}))
  ([^OpenAIClient client opts]
   (with-api-errors
     (let [^BatchService svc (.batches client)
           ^BatchListPage p (.list svc (->batch-list-params opts))]
       (mapv batch->map (.autoPager p))))))
