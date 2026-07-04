(ns openai.core
  "Idiomatic Clojure wrapper over the official OpenAI Java SDK
  (`com.openai/openai-java`), focused on the Responses API."
  (:require [clojure.walk :as walk])
  (:import (com.openai.client OpenAIClient)
           (com.openai.client.okhttp OpenAIOkHttpClient
                                      OpenAIOkHttpClient$Builder)
           (com.openai.core JsonValue)
           (com.openai.models Reasoning
                              Reasoning$Builder
                              ReasoningEffort)
           (com.openai.models.responses EasyInputMessage
                                         EasyInputMessage$Builder
                                         EasyInputMessage$Role
                                         ResponseCreateParams
                                         ResponseCreateParams$Builder
                                         ResponseCreateParams$Input
                                         ResponseCreateParams$Metadata
                                         ResponseCreateParams$Metadata$Builder
                                         ResponseInputItem)))

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

(defn- ->input-message ^ResponseInputItem [{:keys [role content]}]
  (let [^EasyInputMessage$Builder b (EasyInputMessage/builder)]
    (.role b (->role role))
    (.content b ^String content)
    (ResponseInputItem/ofEasyInputMessage (.build b))))

(defn- ->input ^ResponseCreateParams$Input [input]
  (if (string? input)
    (ResponseCreateParams$Input/ofText input)
    (ResponseCreateParams$Input/ofResponse
     ^java.util.List (mapv ->input-message input))))

(defn- ->reasoning ^Reasoning [{:keys [effort]}]
  (let [^Reasoning$Builder b (Reasoning/builder)]
    (when effort (.effort b (ReasoningEffort/of (name effort))))
    (.build b)))

(defn- ->params ^ResponseCreateParams
  [{:keys [model input instructions max-output-tokens temperature top-p
           metadata previous-response-id store reasoning user]}]
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
    (.build b)))
