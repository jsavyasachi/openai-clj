(ns openai.beta.agents.environments-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.errors OpenAIIoException)
           (com.openai.models.beta.agents.environments EnvironmentInfo
                                                          EnvironmentInfo$Builder
                                                          EnvironmentInfo$Status
                                                          EnvironmentInfo$Type
                                                          EnvironmentCreateParams
                                                          EnvironmentListPage
                                                          EnvironmentListPageResponse
                                                          EnvironmentListParams
                                                          EnvironmentRetrieveParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents EnvironmentService)))

(set! *warn-on-reflection* true)

(try
  (require 'openai.beta.agents.environments)
  (catch java.io.FileNotFoundException _))

(defn- api [sym]
  (when-let [n (find-ns 'openai.beta.agents.environments)]
    (some-> (ns-resolve n sym) deref)))

(defn- error-data [f]
  (try
    (f)
    nil
    (catch Throwable e
      (ex-data e))))

(defn- client-for [environment-service]
  (let [agents (proxy [AgentService] []
                 (environments [] environment-service))
        beta (proxy [BetaService] []
               (agents [] agents))]
    (proxy [OpenAIClient] []
      (beta [] beta))))

(defn- environment-info
  ([] (environment-info "env_123" "running"))
  ([id status]
  (let [^EnvironmentInfo$Builder builder (EnvironmentInfo/builder)]
    (.id builder id)
    (.files builder (java.util.Collections/emptyList))
    (.object_ builder (JsonValue/from "agent.environment"))
    (.plugins builder (java.util.Collections/emptyList))
    (.skills builder (java.util.Collections/emptyList))
    (.status builder (EnvironmentInfo$Status/of status))
    (.type builder (EnvironmentInfo$Type/of "openai_hosted"))
    (.putAdditionalProperty builder "future_field" (JsonValue/from "kept"))
    (.build builder))))

(defn- environment-page [service params environments]
  (-> (EnvironmentListPage/builder)
      (.service service)
      (.params params)
      (.response (-> (EnvironmentListPageResponse/builder)
                     (.data environments)
                     (.firstId "env_123")
                     (.hasMore false)
                     (.lastId "env_456")
                     (.object_ (JsonValue/from "list"))
                     (.build)))
      (.build)))

(def environment-request
  {:environment {:environment-template-id "tmpl_123"
                 :capability-directories ["/workspace"]
                 :files [{:type :file-id :file-id "file_123" :path "/workspace/input.txt"}]
                 :setup-commands [{:command "echo ready" :cwd "/workspace"}]
                 :skills [{:type :skill-reference :skill-id "skill_123" :version "1"}]
                 :desktop {:enabled true}
                 :env {"TOKEN" "value"}
                 :network {:access :restricted
                           :allowed-domains ["allowed.example"]
                           :blocked-domains ["blocked.example"]}
                 :packages {:npm ["tsx"] :python ["requests"] :system ["git"]}}
   :vault-ids ["vault_123"]
   :idempotency-key "idem_123"})

(deftest create-environment-builds-params
  (if-let [create-params (api '->create-params)]
    (let [^EnvironmentCreateParams params (create-params environment-request)
          environment (.environment params)]
      (is (= "idem_123" (.get (.idempotencyKey params))))
      (is (= ["vault_123"] (.get (.vaultIds params))))
      (is (= "tmpl_123" (.get (.environmentTemplateId environment))))
      (is (= ["/workspace"] (.get (.capabilityDirectories environment))))
      (is (= "file_123" (.fileId (.asFileId (first (.get (.files environment)))))))
      (is (= "echo ready" (.command (first (.get (.setupCommands environment))))))
      (is (= "skill_123" (.skillId (.asSkillReference (first (.get (.skills environment)))))))
      (is (true? (.enabled (.get (.desktop environment)))))
      (is (= "value" (:token (impl/sdk-object->clj (.get (.env environment))))))
      (is (= "restricted" (.asString (.access (.get (.network environment))))))
      (is (= ["allowed.example"] (.get (.allowedDomains (.get (.network environment))))))
      (is (= ["blocked.example"] (.get (.blockedDomains (.get (.network environment))))))
      (is (= ["tsx"] (.get (.npm (.get (.packages environment))))))
      (is (= ["requests"] (.get (.python (.get (.packages environment))))))
      (is (= ["git"] (.get (.system (.get (.packages environment)))))))
    (is false "openai.beta.agents.environments/create is not implemented")))

(deftest create-environment-requires-environment
  (if-let [create-params (api '->create-params)]
    (is (= {:openai/error :missing-key :key :environment}
           (error-data #(create-params {}))))
    (is false "openai.beta.agents.environments/create is not implemented")))

(deftest create-environment-round-trips
  (if-let [create (api 'create)]
    (let [service (proxy [EnvironmentService] []
                    (create [_] (environment-info "env_123" "ready")))]
      (is (= {:id "env_123" :status :ready}
             (select-keys (create (client-for service) environment-request) [:id :status]))))
    (is false "openai.beta.agents.environments/create is not implemented")))

(deftest list-environments-builds-params
  (if-let [list-params (api '->list-params)]
    (let [^EnvironmentListParams params
          (list-params {:after "env_123" :limit 2 :order :desc :type :openai-hosted})]
      (is (= "env_123" (.get (.after params))))
      (is (= 2 (.get (.limit params))))
      (is (= "desc" (.asString (.get (.order params)))))
      (is (= "openai_hosted" (.asString (.get (.type params))))))
    (is false "openai.beta.agents.environments/list is not implemented")))

(deftest list-environments-round-trips
  (if-let [list-environments (api 'list)]
    (let [service-ref (atom nil)
          service (proxy [EnvironmentService] []
                    (list [params]
                      (environment-page @service-ref params
                                        [(environment-info "env_123" "ready")
                                         (environment-info "env_456" "failed")])))]
      (reset! service-ref service)
      (is (= [{:id "env_123" :status :ready}
              {:id "env_456" :status :failed}]
             (mapv #(select-keys % [:id :status])
                   (list-environments (client-for service) {})))))
    (is false "openai.beta.agents.environments/list is not implemented")))

(deftest retrieves-environment
  (if-let [retrieve (api 'retrieve)]
    (let [captured (atom nil)
          service (proxy [EnvironmentService] []
                    (retrieve [params]
                      (reset! captured params)
                      (environment-info)))
          result (retrieve (client-for service) "env_123")]
      (testing "calls the nested environment service with a typed id parameter"
        (is (instance? EnvironmentRetrieveParams @captured))
        (is (= "env_123" (.get (.environmentId ^EnvironmentRetrieveParams @captured)))))
      (testing "normalizes response keys and enum values"
        (is (= {:id "env_123"
                :files []
                :object "agent.environment"
                :plugins []
                :skills []
                :status :running
                :type :openai-hosted
                :future-field "kept"}
               result)))
      (testing "validates required parameters"
        (is (= {:openai/error :missing-key :key :environment-id}
               (error-data #(retrieve (client-for service) nil))))))
    (is false "openai.beta.agents.environments/retrieve is not implemented")))

(deftest wraps-environment-api-errors
  (if-let [retrieve (api 'retrieve)]
    (let [service (proxy [EnvironmentService] []
                    (retrieve [_]
                      (throw (OpenAIIoException. "network failed"))))]
      (is (= :io-error
             (:openai/error
              (error-data #(retrieve (client-for service) "env_123"))))))
    (is false "openai.beta.agents.environments/retrieve is not implemented")))
