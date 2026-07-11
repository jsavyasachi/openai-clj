(ns openai.evals
  "Idiomatic Clojure wrapper over the OpenAI Evals API."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.evals EvalCreateParams EvalCreateParams$DataSourceConfig
                                      EvalCreateParams$DataSourceConfig$Custom
                                      EvalCreateParams$DataSourceConfig$Custom$ItemSchema
                                      EvalCreateParams$DataSourceConfig$Logs
                                      EvalCreateParams$DataSourceConfig$StoredCompletions
                                      EvalDeleteResponse EvalListPage EvalListParams
                                      EvalListParams$Order EvalListParams$OrderBy)
           (com.openai.models.evals.runs CreateEvalCompletionsRunDataSource
                                           CreateEvalJsonlRunDataSource RunCreateParams
                                           RunListPage RunListParams RunListParams$Order
                                           RunListParams$Status)
           (com.openai.models.evals.runs.outputitems OutputItemListPage
                                                       OutputItemListParams
                                                       OutputItemListParams$Order
                                                       OutputItemListParams$Status
                                                       OutputItemRetrieveParams)
           (com.openai.models.graders.gradermodels StringCheckGrader
                                                    StringCheckGrader$Operation)
           (com.openai.services.blocking EvalService)
           (com.openai.services.blocking.evals RunService)
           (com.openai.services.blocking.evals.runs OutputItemService)))
(set! *warn-on-reflection* true)

(defn- ->data-source-config ^EvalCreateParams$DataSourceConfig
  [{:keys [type item-schema include-sample-schema] :as config}]
  (case (keyword type)
    :custom
    (let [schema (-> (EvalCreateParams$DataSourceConfig$Custom$ItemSchema/builder)
                     (.additionalProperties ^java.util.Map (impl/->json-schema-properties item-schema))
                     (.build))
          b (EvalCreateParams$DataSourceConfig$Custom/builder)]
      (.itemSchema b schema)
      (when (some? include-sample-schema) (.includeSampleSchema b (boolean include-sample-schema)))
      (EvalCreateParams$DataSourceConfig/ofCustom (.build b)))
    :logs
    (let [b (EvalCreateParams$DataSourceConfig$Logs/builder)]
      (.type b (JsonValue/from "logs"))
      (doseq [[k v] (dissoc config :type)] (.putAdditionalProperty b (name k) (JsonValue/from v)))
      (EvalCreateParams$DataSourceConfig/ofLogs (.build b)))
    :stored-completions
    (let [b (EvalCreateParams$DataSourceConfig$StoredCompletions/builder)]
      (.type b (JsonValue/from "stored_completions"))
      (doseq [[k v] (dissoc config :type)] (.putAdditionalProperty b (name k) (JsonValue/from v)))
      (EvalCreateParams$DataSourceConfig/ofStoredCompletions (.build b)))
    (throw (ex-info (str "Unsupported eval data source " type)
                    {:openai/error :unsupported-data-source :type type}))))

(defn- ->criterion [{:keys [type name input reference operation]}]
  (case (keyword type)
    :string-check
    (-> (StringCheckGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.operation (StringCheckGrader$Operation/of (impl/enum-name operation))) (.build))
    (throw (ex-info (str "Unsupported testing criterion " type)
                    {:openai/error :unsupported-testing-criterion :type type}))))

(defn- ->create-params ^EvalCreateParams
  [{:keys [data-source-config testing-criteria name metadata]}]
  (when-not data-source-config (impl/missing-key! :data-source-config))
  (when-not testing-criteria (impl/missing-key! :testing-criteria))
  (let [b (EvalCreateParams/builder)]
    (.dataSourceConfig b (->data-source-config data-source-config))
    (doseq [criterion testing-criteria]
      (.addTestingCriterion b ^StringCheckGrader (->criterion criterion)))
    (when name (.name b ^String name))
    (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
    (.build b)))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors (let [^EvalService svc (.evals client)]
                          (impl/sdk-object->clj (.create svc (->create-params req))))))
(defn retrieve [^OpenAIClient client ^String eval-id]
  (impl/with-api-errors (let [^EvalService svc (.evals client)]
                          (impl/sdk-object->clj (.retrieve svc eval-id)))))
(defn update [^OpenAIClient client ^String eval-id {:keys [name metadata]}]
  (impl/with-api-errors
    (let [b (com.openai.models.evals.EvalUpdateParams/builder)
          _ (.evalId b eval-id) _ (when name (.name b ^String name))
          _ (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
          ^EvalService svc (.evals client)]
      (impl/sdk-object->clj (.update svc (.build b))))))

(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client {:keys [after limit order order-by]}]
   (impl/with-api-errors
     (let [b (EvalListParams/builder)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (EvalListParams$Order/of (name order))))
           _ (when order-by (.orderBy b (EvalListParams$OrderBy/of (impl/enum-name order-by))))
           ^EvalService svc (.evals client) ^EvalListPage page (.list svc (.build b))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))
(defn delete [^OpenAIClient client ^String eval-id]
  (impl/with-api-errors
    (let [^EvalService svc (.evals client) ^EvalDeleteResponse d (.delete svc eval-id)]
      (impl/sdk-object->clj d))))

(defn- ->run-data-source [{:keys [type source model] :as ds}]
  (case (keyword type)
    :jsonl (let [b (CreateEvalJsonlRunDataSource/builder)]
             (.fileIdSource b ^String (:file-id source)) (.build b))
    :completions (let [b (CreateEvalCompletionsRunDataSource/builder)]
                   (.fileIdSource b ^String (:file-id source))
                   (when model (.model b ^String model)) (.build b))
    (throw (ex-info (str "Unsupported run data source " type)
                    {:openai/error :unsupported-data-source :type type}))))

(defn- ->run-create-params ^RunCreateParams [^String eval-id {:keys [data-source name metadata]}]
  (when-not data-source (impl/missing-key! :data-source))
  (let [b (RunCreateParams/builder) ds (->run-data-source data-source)]
    (.evalId b eval-id)
    (cond (instance? CreateEvalJsonlRunDataSource ds) (.dataSource b ^CreateEvalJsonlRunDataSource ds)
          (instance? CreateEvalCompletionsRunDataSource ds) (.dataSource b ^CreateEvalCompletionsRunDataSource ds))
    (when name (.name b ^String name))
    (when metadata (.putAdditionalBodyProperty b "metadata" (JsonValue/from metadata)))
    (.build b)))
(defn create-run [^OpenAIClient client ^String eval-id req]
  (impl/with-api-errors (let [^RunService svc (.runs (.evals client))]
                          (impl/sdk-object->clj (.create svc (->run-create-params eval-id req))))))

(defn retrieve-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunRetrieveParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (impl/sdk-object->clj (.retrieve svc p)))))

(defn list-runs
  ([^OpenAIClient client ^String eval-id] (list-runs client eval-id {}))
  ([^OpenAIClient client ^String eval-id {:keys [after limit order status]}]
   (impl/with-api-errors
     (let [b (RunListParams/builder) _ (.evalId b eval-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (RunListParams$Order/of (name order))))
           _ (when status (.status b (RunListParams$Status/of (impl/enum-name status))))
           ^RunService svc (.runs (.evals client)) ^RunListPage page (.list svc (.build b))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))
(defn cancel-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunCancelParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (impl/sdk-object->clj (.cancel svc p)))))
(defn delete-run [^OpenAIClient client ^String eval-id ^String run-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.evals.runs.RunDeleteParams/builder)
                (.evalId eval-id) (.runId run-id) (.build))
          ^RunService svc (.runs (.evals client))]
      (impl/sdk-object->clj (.delete svc p)))))

(defn- ->output-list-params ^OutputItemListParams
  [^String eval-id ^String run-id {:keys [after limit order status]}]
  (let [b (OutputItemListParams/builder)]
    (.evalId b eval-id) (.runId b run-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (OutputItemListParams$Order/of (name order))))
    (when status (.status b (OutputItemListParams$Status/of (impl/enum-name status))))
    (.build b)))
(defn list-output-items
  ([^OpenAIClient client ^String eval-id ^String run-id]
   (list-output-items client eval-id run-id {}))
  ([^OpenAIClient client ^String eval-id ^String run-id opts]
   (impl/with-api-errors
     (let [^OutputItemService svc (.outputItems (.runs (.evals client)))
           ^OutputItemListPage page (.list svc (->output-list-params eval-id run-id opts))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))
(defn retrieve-output-item [^OpenAIClient client ^String eval-id ^String run-id ^String item-id]
  (impl/with-api-errors
    (let [p (-> (OutputItemRetrieveParams/builder) (.evalId eval-id) (.runId run-id)
                (.outputItemId item-id) (.build))
          ^OutputItemService svc (.outputItems (.runs (.evals client)))]
      (impl/sdk-object->clj (.retrieve svc p)))))

(def run-create create-run)
(def run-retrieve retrieve-run)
(def run-list list-runs)
(def run-cancel cancel-run)
(def run-delete delete-run)
(def output-item-list list-output-items)
(def output-item-retrieve retrieve-output-item)
