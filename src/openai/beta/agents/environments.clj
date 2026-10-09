(ns openai.beta.agents.environments
  "Clojure wrapper for the beta Agents Environments API."
  (:refer-clojure :exclude [list])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents HostedEnvironmentFileParam
                                           HostedPluginParam
                                           HostedSkillParam
                                           SetupCommandParam)
           (com.openai.models.beta.agents.environments EnvironmentInfo
                                                          EnvironmentCreateParams
                                                          EnvironmentCreateParams$Builder
                                                          EnvironmentCreateParams$Environment
                                                          EnvironmentCreateParams$Environment$Builder
                                                          EnvironmentCreateParams$Environment$Desktop
                                                          EnvironmentCreateParams$Environment$Env
                                                          EnvironmentCreateParams$Environment$Network
                                                          EnvironmentCreateParams$Environment$Packages
                                                          EnvironmentListPage
                                                          EnvironmentListParams
                                                          EnvironmentListParams$Order
                                                          EnvironmentListParams$Type
                                                          EnvironmentRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)))

(set! *warn-on-reflection* true)

(defn- normalize-value [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (#{:type :status} k) (string? v))
                      (impl/->keyword v)
                      (normalize-value v))]))
          value)

    (vector? value) (mapv normalize-value value)
    :else value))

(defn- environment-service ^EnvironmentService [^OpenAIClient client]
  (let [^BetaService beta (.beta client)
        ^AgentService agents (.agents beta)]
    (.environments agents)))

(defn- ->retrieve-params ^EnvironmentRetrieveParams [environment-id]
  (when-not environment-id (impl/missing-key! :environment-id))
  (-> (EnvironmentRetrieveParams/builder)
      (.environmentId ^String environment-id)
      (.build)))

(defn- ->environment ^EnvironmentCreateParams$Environment [environment]
  (let [^EnvironmentCreateParams$Environment$Builder b
        (EnvironmentCreateParams$Environment/builder)
        {:keys [capability-directories desktop env environment-template-id files network
                packages plugins setup-commands skills]} environment]
    (.type b (JsonValue/from "openai_hosted"))
    (when capability-directories
      (.capabilityDirectories b ^java.util.List (vec capability-directories)))
    (when desktop
      (.desktop b ^EnvironmentCreateParams$Environment$Desktop
                (impl/sdk-input-object desktop
                                       EnvironmentCreateParams$Environment$Desktop)))
    (when env
      (.env b ^EnvironmentCreateParams$Environment$Env
            (impl/sdk-input-object env EnvironmentCreateParams$Environment$Env)))
    (when environment-template-id
      (.environmentTemplateId b ^String environment-template-id))
    (when files
      (.files b ^java.util.List
              (mapv #(impl/sdk-input-object % HostedEnvironmentFileParam) files)))
    (when network
      (.network b ^EnvironmentCreateParams$Environment$Network
                (impl/sdk-input-object network
                                       EnvironmentCreateParams$Environment$Network)))
    (when packages
      (.packages b ^EnvironmentCreateParams$Environment$Packages
                 (impl/sdk-input-object packages
                                        EnvironmentCreateParams$Environment$Packages)))
    (when plugins
      (.plugins b ^java.util.List
                (mapv #(impl/sdk-input-object % HostedPluginParam) plugins)))
    (when setup-commands
      (.setupCommands b ^java.util.List
                      (mapv #(impl/sdk-input-object % SetupCommandParam) setup-commands)))
    (when skills
      (.skills b ^java.util.List
               (mapv #(impl/sdk-input-object % HostedSkillParam) skills)))
    (.build b)))

(defn- ->create-params ^EnvironmentCreateParams
  [{:keys [environment idempotency-key vault-ids]}]
  (when-not environment (impl/missing-key! :environment))
  (let [^EnvironmentCreateParams$Builder b (EnvironmentCreateParams/builder)]
    (.environment b (->environment environment))
    (when idempotency-key (.idempotencyKey b ^String idempotency-key))
    (when vault-ids (.vaultIds b ^java.util.List (vec vault-ids)))
    (.build b)))

(defn- ->list-params ^EnvironmentListParams
  [{:keys [after limit order type]}]
  (let [b (EnvironmentListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (EnvironmentListParams$Order/of (impl/enum-name order))))
    (when type
      (.type b (EnvironmentListParams$Type/of (impl/enum-name type))))
    (.build b)))

(defn- environment-info->map [^EnvironmentInfo environment]
  (normalize-value (impl/sdk-object->clj environment)))

(defn retrieve
  "Retrieve a hosted agent environment by id."
  [^OpenAIClient client environment-id]
  (impl/with-api-errors
    (let [^EnvironmentService service (environment-service client)]
      (environment-info->map
       (.retrieve service (->retrieve-params environment-id))))))

(defn create
  "Create a hosted agent environment."
  [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^EnvironmentService service (environment-service client)]
      (environment-info->map (.create service (->create-params req))))))

(defn list
  "List hosted agent environments."
  ([^OpenAIClient client]
   (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^EnvironmentService service (environment-service client)
           ^EnvironmentListPage page (.list service (->list-params opts))]
       (mapv environment-info->map (impl/all-pages page))))))
