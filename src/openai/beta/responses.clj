(ns openai.beta.responses
  "Clojure wrapper for the beta Responses API."
  (:refer-clojure :exclude [compact])
  (:require [clojure.string :as str]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.core.http StreamResponse)
           (com.openai.models.beta.responses BetaCompactedResponse
                                             BetaEasyInputMessage
                                             BetaResponse
                                             BetaResponseIncludable
                                             BetaResponseInputItem
                                             BetaResponseInputItem$Message
                                             BetaResponseInputItem$Message$Builder
                                             BetaResponseInputItem$Message$Role
                                             BetaResponseStreamEvent
                                             BetaToolChoiceOptions
                                             ResponseCreateParams
                                             ResponseCreateParams$Beta
                                             ResponseCreateParams$Builder
                                             ResponseCreateParams$Metadata
                                             ResponseCreateParams$Metadata$Builder
                                             ResponseCreateParams$MultiAgent
                                             ResponseCreateParams$MultiAgent$Builder
                                             ResponseCreateParams$PromptCacheOptions
                                             ResponseCreateParams$PromptCacheOptions$Builder
                                             ResponseCreateParams$PromptCacheOptions$Mode
                                             ResponseCreateParams$PromptCacheOptions$Ttl
                                             ResponseCreateParams$Reasoning
                                             ResponseCreateParams$Reasoning$Builder
                                             ResponseCreateParams$Reasoning$Effort
                                             ResponseCreateParams$Reasoning$Mode
                                             ResponseCreateParams$StreamOptions
                                             ResponseCreateParams$StreamOptions$Builder
                                             ResponseCreateParams$ToolChoice
                                             ResponseCreateParams$ServiceTier
                                             ResponseCreateParams$Truncation
                                             BetaToolChoiceFunction
                                             BetaToolChoiceFunction$Builder
                                             ResponseCancelParams
                                             ResponseDeleteParams
                                             ResponseRetrieveParams
                                             ResponseCompactParams
                                             ResponseCompactParams$ServiceTier
                                             ResponseRetrieveParams$Builder
                                             ResponseCancelParams$Builder
                                             ResponseDeleteParams$Builder
                                             ResponseCompactParams$Builder)
           (com.openai.models.beta.responses.inputitems InputItemListPage
                                                         InputItemListParams
                                                         InputItemListParams$Builder
                                                         InputItemListParams$Order)
           (com.openai.models.beta.responses.inputtokens InputTokenCountParams
                                                         InputTokenCountParams$Builder
                                                         InputTokenCountParams$Beta
                                                         InputTokenCountParams$Truncation
                                                         InputTokenCountResponse)
           (com.openai.services.blocking.beta ResponseService)
           (com.openai.services.blocking.beta.responses InputItemService InputTokenService)))

(set! *warn-on-reflection* true)

(defn- ->message-input-item ^BetaResponseInputItem [{:keys [role content]}]
  (when-not role (impl/missing-key! :role))
  (when-not content (impl/missing-key! :content))
  (let [^BetaResponseInputItem$Message$Builder b (BetaResponseInputItem$Message/builder)]
    (.role b (BetaResponseInputItem$Message$Role/of (name role)))
    (if (string? content)
      (.addInputTextContent b ^String content)
      (doseq [{:keys [text]} content]
        (.addInputTextContent b ^String text)))
    (BetaResponseInputItem/ofMessage (.build b))))

(defn- ->input-item ^BetaResponseInputItem [{:keys [type] :as item}]
  (case (keyword type)
    :message (->message-input-item item)
    nil (->message-input-item item)
    (throw (ex-info (str "Unknown beta input type " type)
                    {:openai/error :unknown-input-type :type type}))))

(defn- ->input [input]
  (if (string? input)
    input
    (mapv ->input-item input)))

(defn- ->metadata ^ResponseCreateParams$Metadata [m]
  (let [^ResponseCreateParams$Metadata$Builder b (ResponseCreateParams$Metadata/builder)]
    (.additionalProperties b ^java.util.Map (impl/->json-value-properties m))
    (.build b)))

(defn- ->multi-agent ^ResponseCreateParams$MultiAgent [{:keys [enabled max-concurrent-subagents]}]
  (let [^ResponseCreateParams$MultiAgent$Builder b (ResponseCreateParams$MultiAgent/builder)]
    (when (some? enabled) (.enabled b (boolean enabled)))
    (when (some? max-concurrent-subagents)
      (.maxConcurrentSubagents b (long max-concurrent-subagents)))
    (.build b)))

(defn- ->reasoning ^ResponseCreateParams$Reasoning [{:keys [effort mode]}]
  (let [^ResponseCreateParams$Reasoning$Builder b (ResponseCreateParams$Reasoning/builder)]
    (when effort (.effort b (ResponseCreateParams$Reasoning$Effort/of (name effort))))
    (when mode (.mode b (name mode)))
    (.build b)))

(defn- ->prompt-cache-options ^ResponseCreateParams$PromptCacheOptions [{:keys [mode ttl]}]
  (let [^ResponseCreateParams$PromptCacheOptions$Builder b
        (ResponseCreateParams$PromptCacheOptions/builder)]
    (when mode (.mode b (ResponseCreateParams$PromptCacheOptions$Mode/of (name mode))))
    (when ttl (.ttl b (ResponseCreateParams$PromptCacheOptions$Ttl/of (name ttl))))
    (.build b)))

(defn- ->stream-options ^ResponseCreateParams$StreamOptions [{:keys [include-obfuscation]}]
  (let [^ResponseCreateParams$StreamOptions$Builder b (ResponseCreateParams$StreamOptions/builder)]
    (when (some? include-obfuscation) (.includeObfuscation b (boolean include-obfuscation)))
    (.build b)))

(defn- ->tool-choice ^ResponseCreateParams$ToolChoice [choice]
  (if (map? choice)
    (if (= :function (keyword (:type choice)))
      (let [^BetaToolChoiceFunction$Builder b (BetaToolChoiceFunction/builder)]
        (.name b ^String (:name choice))
        (ResponseCreateParams$ToolChoice/ofBetaToolChoiceFunction (.build b)))
      (throw (ex-info (str "Unknown beta tool choice type " (:type choice))
                      {:openai/error :unknown-tool-choice-type :type (:type choice)})))
    (case (keyword choice)
      :auto (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/AUTO)
      :required (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/REQUIRED)
      :none (ResponseCreateParams$ToolChoice/ofBetaToolChoiceOptions BetaToolChoiceOptions/NONE)
      :programmatic-tool-calling (ResponseCreateParams$ToolChoice/ofBetaSpecificProgrammaticToolCallingParam)
      (throw (ex-info (str "Unknown beta tool choice " choice)
                      {:openai/error :unknown-tool-choice :tool-choice choice})))))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p metadata previous-response-id
           store reasoning user tool-choice parallel-tool-calls background include truncation
           prompt-cache-key prompt-cache-options safety-identifier service-tier max-tool-calls
           top-logprobs stream-options betas multi-agent]}]
  (when-not model (impl/missing-key! :model))
  (when-not input (impl/missing-key! :input))
  (let [^ResponseCreateParams$Builder b (ResponseCreateParams/builder)]
    (.model b ^String model)
    (if (string? input)
      (.input b ^String input)
      (.inputOfBetaResponse b ^java.util.List (mapv ->input-item input)))
    (doseq [beta betas]
      (.addBeta b (ResponseCreateParams$Beta/of (impl/enum-name beta))))
    (when instructions (.instructions b ^String instructions))
    (when max-output-tokens (.maxOutputTokens b (long max-output-tokens)))
    (when max-tool-calls (.maxToolCalls b (long max-tool-calls)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when top-logprobs (.topLogprobs b (long top-logprobs)))
    (when (some? background) (.background b (boolean background)))
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when truncation (.truncation b (ResponseCreateParams$Truncation/of (impl/enum-name truncation))))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when prompt-cache-options (.promptCacheOptions b (->prompt-cache-options prompt-cache-options)))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (when service-tier (.serviceTier b (ResponseCreateParams$ServiceTier/of (impl/enum-name service-tier))))
    (when metadata (.metadata b (->metadata metadata)))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? store) (.store b (boolean store)))
    (when reasoning (.reasoning b (->reasoning reasoning)))
    (when user (.user b ^String user))
    (when tool-choice
      (if (= :programmatic-tool-calling (keyword tool-choice))
        (.toolChoiceBetaSpecificProgrammaticToolCallingParam b)
        (.toolChoice b (->tool-choice tool-choice))))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when stream-options (.streamOptions b (->stream-options stream-options)))
    (when multi-agent (.multiAgent b (->multi-agent multi-agent)))
    (.build b)))

(defn- ->retrieve-params ^ResponseRetrieveParams
  [^String response-id {:keys [include include-obfuscation starting-after betas]}]
  (let [^ResponseRetrieveParams$Builder b (ResponseRetrieveParams/builder)]
    (.responseId b response-id)
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when (some? include-obfuscation) (.includeObfuscation b (boolean include-obfuscation)))
    (when (some? starting-after) (.startingAfter b (long starting-after)))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.ResponseRetrieveParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->cancel-params ^ResponseCancelParams [^String response-id]
  (let [^ResponseCancelParams$Builder b (ResponseCancelParams/builder)]
    (.responseId b response-id)
    (.build b)))

(defn- ->delete-params ^ResponseDeleteParams [^String response-id]
  (let [^ResponseDeleteParams$Builder b (ResponseDeleteParams/builder)]
    (.responseId b response-id)
    (.build b)))

(defn- ->compact-params ^ResponseCompactParams
  [{:keys [model previous-response-id input instructions prompt-cache-key service-tier betas]}]
  (let [^ResponseCompactParams$Builder b (ResponseCompactParams/builder)]
    (when model (.model b ^String model))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfBetaResponseInputItems b ^java.util.List (mapv ->input-item input))))
    (when instructions (.instructions b ^String instructions))
    (when prompt-cache-key (.promptCacheKey b ^String prompt-cache-key))
    (when service-tier (.serviceTier b (ResponseCompactParams$ServiceTier/of (impl/enum-name service-tier))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.ResponseCompactParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->input-item-list-params ^InputItemListParams
  [^String response-id {:keys [after include limit order betas]}]
  (let [^InputItemListParams$Builder b (InputItemListParams/builder)]
    (.responseId b response-id)
    (when after (.after b ^String after))
    (doseq [i include] (.addInclude b (BetaResponseIncludable/of (impl/enum-name i))))
    (when limit (.limit b (long limit)))
    (when order (.order b (InputItemListParams$Order/of (impl/enum-name order))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.inputitems.InputItemListParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- ->input-token-count-params ^InputTokenCountParams
  [{:keys [model input instructions previous-response-id parallel-tool-calls truncation betas]}]
  (let [^InputTokenCountParams$Builder b (InputTokenCountParams/builder)]
    (when model (.model b ^String model))
    (when input
      (if (string? input)
        (.input b ^String input)
        (.inputOfBetaResponseInputItems b ^java.util.List (mapv ->input-item input))))
    (when instructions (.instructions b ^String instructions))
    (when previous-response-id (.previousResponseId b ^String previous-response-id))
    (when (some? parallel-tool-calls) (.parallelToolCalls b (boolean parallel-tool-calls)))
    (when truncation (.truncation b (InputTokenCountParams$Truncation/of (impl/enum-name truncation))))
    (doseq [beta betas]
      (.addBeta b (com.openai.models.beta.responses.inputtokens.InputTokenCountParams$Beta/of
                   (impl/enum-name beta))))
    (.build b)))

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:type :status :role} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)
    (vector? value) (mapv normalize-value value)
    :else value))

(defn- output-text [items]
  (apply str
         (for [item items
               :when (= :message (:type item))
               content (:content item)
               :when (#{:text :output-text} (:type content))]
           (:text content))))

(defn- beta-response-data->map ^clojure.lang.IPersistentMap [m]
  (let [items (mapv normalize-value (:output m))]
    (cond-> {:id (:id m)
             :model (:model m)
             :output items
             :text (output-text items)
             :created-at (:created-at m)}
      (:status m) (assoc :status (:status m))
      (:usage m) (assoc :usage (:usage m))
      (:error m) (assoc :error (:error m))
      (:incomplete-details m) (assoc :incomplete-details (:incomplete-details m))
      (:previous-response-id m) (assoc :previous-response-id (:previous-response-id m)))))

(defn- beta-response->map ^clojure.lang.IPersistentMap [^BetaResponse response]
  (beta-response-data->map (normalize-value (impl/sdk-object->clj response))))

(defn- beta-compacted-response->map ^clojure.lang.IPersistentMap
  [^BetaCompactedResponse response]
  (let [m (normalize-value (impl/sdk-object->clj response))
        items (mapv normalize-value (:output m))]
    {:id (:id m)
     :output items
     :text (output-text items)
     :usage (:usage m)
     :created-at (:created-at m)}))

(defn- input-token-count-response->map ^clojure.lang.IPersistentMap
  [^InputTokenCountResponse response]
  {:input-tokens (:input-tokens (impl/sdk-object->clj response))})

(defn- event->map ^clojure.lang.IPersistentMap [^BetaResponseStreamEvent event]
  (let [m (normalize-value (impl/sdk-object->clj event))
        type (some-> (:type m) name
                     (str/replace-first #"^response[.-]" "")
                     (str/replace "_" "-")
                     (str/replace "." "-")
                     keyword)
        type (case type
               :text-delta :output-text-delta
               :text-done :output-text-done
               type)]
    (case type
      :output-text-delta (select-keys (assoc m :type type) [:type :delta :item-id :output-index])
      :output-text-done (select-keys (assoc m :type type) [:type :text :item-id :output-index])
      :created {:type :created}
      :in-progress {:type :in-progress}
      :completed {:type :completed
                  :response (beta-response-data->map (:response m))}
      :incomplete {:type :incomplete
                   :response (beta-response-data->map (:response m))}
      :failed {:type :failed
               :response (beta-response-data->map (:response m))}
      :queued {:type :queued
               :response (beta-response-data->map (:response m))}
      :error (select-keys (assoc m :type type) [:type :message :code])
      (assoc m :type type))))

(defn- drain-stream ^String [^StreamResponse stream on-event]
  (let [sb (StringBuilder.)]
    (doseq [event (iterator-seq (.iterator (.stream stream)))]
      (let [m (event->map event)]
        (when (= :output-text-delta (:type m)) (.append sb ^String (:delta m)))
        (when on-event (on-event m))))
    (str sb)))

(defn create-response [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.create svc (->params req))))))

(defn count-input-tokens [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))
          ^InputTokenService tokens (.inputTokens svc)]
      (input-token-count-response->map (.count tokens (->input-token-count-params req))))))

(defn stream ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (with-open [^StreamResponse stream (.createStreaming svc (->params req))]
        (drain-stream stream on-event)))))

(defn retrieve-streaming ^String [^OpenAIClient client ^String response-id on-event]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (with-open [^StreamResponse stream
                  (.retrieveStreaming svc (->retrieve-params response-id {}))]
        (drain-stream stream on-event)))))

(defn stream-text [^OpenAIClient client req on-text]
  (stream client req
          (fn [m]
            (when (and on-text (= :output-text-delta (:type m)))
              (on-text (:delta m))))))

(defn get-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.retrieve svc (->retrieve-params response-id {}))))))

(defn list-input-items
  ([^OpenAIClient client ^String response-id] (list-input-items client response-id {}))
  ([^OpenAIClient client ^String response-id opts]
   (impl/with-api-errors
     (let [^ResponseService svc (.. client (beta) (responses))
           ^InputItemService items (.inputItems svc)
           ^InputItemListPage page (.list items (->input-item-list-params response-id opts))]
       (mapv #(normalize-value (impl/sdk-object->clj %)) (impl/all-pages page))))))

(defn delete-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (.delete svc (->delete-params response-id)))
    nil))

(defn cancel-response [^OpenAIClient client ^String response-id]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-response->map (.cancel svc (->cancel-params response-id))))))

(defn compact [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ResponseService svc (.. client (beta) (responses))]
      (beta-compacted-response->map (.compact svc (->compact-params req))))))
