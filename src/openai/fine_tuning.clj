(ns openai.fine-tuning
  "Idiomatic Clojure wrapper over the OpenAI Fine-tuning API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.finetuning.jobs FineTuningJob JobCreateParams
                                                JobListEventsPage JobListPage)
           (com.openai.models.finetuning.jobs.checkpoints CheckpointListPage)
           (com.openai.models.finetuning.checkpoints.permissions PermissionCreatePage
                                                                   PermissionDeleteResponse
                                                                   PermissionListPage)
           (com.openai.models.finetuning.alpha.graders GraderRunParams GraderValidateParams)
           (com.openai.models.graders.gradermodels StringCheckGrader
                                                    StringCheckGrader$Operation
                                                    TextSimilarityGrader
                                                    TextSimilarityGrader$EvaluationMetric)
           (com.openai.services.blocking FineTuningService)
           (com.openai.services.blocking.finetuning JobService)
           (com.openai.services.blocking.finetuning.jobs CheckpointService)
           (com.openai.services.blocking.finetuning.checkpoints PermissionService)))

(set! *warn-on-reflection* true)

(defn- ->job-create-params ^JobCreateParams
  [{:keys [model training-file validation-file suffix seed metadata
           hyperparameters method integrations]}]
  (when-not model (impl/missing-key! :model))
  (when-not training-file (impl/missing-key! :training-file))
  (let [b (JobCreateParams/builder)]
    (.model b ^String model) (.trainingFile b ^String training-file)
    (when validation-file (.validationFile b ^String validation-file))
    (when suffix (.suffix b ^String suffix))
    (when seed (.seed b (long seed)))
    (doseq [[k v] [[:metadata metadata] [:hyperparameters hyperparameters]
                    [:method method] [:integrations integrations]] :when v]
      (.putAdditionalBodyProperty b (impl/enum-name k) (JsonValue/from v)))
    (.build b)))

(defn- job->map [^FineTuningJob j]
  (cond-> {:id (.id j) :model (.model j)
           :status (impl/->keyword (.asString (.status j)))
           :created-at (.createdAt j) :training-file (.trainingFile j)
           :hyperparameters (impl/sdk-object->clj (.hyperparameters j))
           :result-files (vec (.resultFiles j)) :seed (.seed j)}
    (.isPresent (.finishedAt j)) (assoc :finished-at (impl/opt-get (.finishedAt j)))
    (.isPresent (.fineTunedModel j)) (assoc :fine-tuned-model (impl/opt-get (.fineTunedModel j)))
    (.isPresent (.validationFile j)) (assoc :validation-file (impl/opt-get (.validationFile j)))
    (.isPresent (.trainedTokens j)) (assoc :trained-tokens (impl/opt-get (.trainedTokens j)))
    (.isPresent (.error j)) (assoc :error (impl/sdk-object->clj (impl/opt-get (.error j))))
    (.isPresent (.metadata j)) (assoc :metadata (impl/sdk-object->clj (impl/opt-get (.metadata j))))))

(defn create-job [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (.create svc (->job-create-params req))))))

(defn retrieve-job [^OpenAIClient client ^String job-id]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (.retrieve svc job-id)))))

(defn- ->job-list-params ^com.openai.models.finetuning.jobs.JobListParams
  [{:keys [after limit metadata]}]
  (let [b (com.openai.models.finetuning.jobs.JobListParams/builder)]
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when metadata (.putAdditionalQueryParam b "metadata" (str metadata)))
    (.build b)))

(defn list-jobs
  ([^OpenAIClient client] (list-jobs client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^JobService svc (.jobs (.fineTuning client))
           ^JobListPage page (.list svc (->job-list-params opts))]
       (mapv job->map (impl/all-pages page))))))

(defn- job-action [^OpenAIClient client ^String job-id action]
  (impl/with-api-errors
    (let [^JobService svc (.jobs (.fineTuning client))]
      (job->map (case action :cancel (.cancel svc job-id)
                      :pause (.pause svc job-id) :resume (.resume svc job-id))))))
(defn cancel-job [client job-id] (job-action client job-id :cancel))
(defn pause-job [client job-id] (job-action client job-id :pause))
(defn resume-job [client job-id] (job-action client job-id :resume))

(defn list-events
  ([^OpenAIClient client ^String job-id] (list-events client job-id {}))
  ([^OpenAIClient client ^String job-id {:keys [after limit]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.jobs.JobListEventsParams/builder)
           _ (.fineTuningJobId b job-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           ^JobService svc (.jobs (.fineTuning client))
           ^JobListEventsPage page (.listEvents svc (.build b))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))

(defn list-checkpoints
  ([^OpenAIClient client ^String job-id] (list-checkpoints client job-id {}))
  ([^OpenAIClient client ^String job-id {:keys [after limit]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.jobs.checkpoints.CheckpointListParams/builder)
           _ (.fineTuningJobId b job-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           ^CheckpointService svc (.checkpoints (.jobs (.fineTuning client)))
           ^CheckpointListPage page (.list svc (.build b))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))

(defn create-permission [^OpenAIClient client ^String checkpoint-id project-ids]
  (impl/with-api-errors
    (let [b (com.openai.models.finetuning.checkpoints.permissions.PermissionCreateParams/builder)
          _ (.fineTunedModelCheckpoint b checkpoint-id)
          _ (.projectIds b ^java.util.List project-ids)
          ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
          ^PermissionCreatePage page (.create svc (.build b))]
      (mapv impl/sdk-object->clj (impl/all-pages page)))))

(defn list-permissions
  ([^OpenAIClient client ^String checkpoint-id] (list-permissions client checkpoint-id {}))
  ([^OpenAIClient client ^String checkpoint-id {:keys [after limit order]}]
   (impl/with-api-errors
     (let [b (com.openai.models.finetuning.checkpoints.permissions.PermissionListParams/builder)
           _ (.fineTunedModelCheckpoint b checkpoint-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (com.openai.models.finetuning.checkpoints.permissions.PermissionListParams$Order/of (name order))))
           ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
           ^PermissionListPage page (.list svc (.build b))]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))

(defn delete-permission [^OpenAIClient client ^String checkpoint-id ^String permission-id]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.finetuning.checkpoints.permissions.PermissionDeleteParams/builder)
                (.fineTunedModelCheckpoint checkpoint-id) (.permissionId permission-id) (.build))
          ^PermissionService svc (.permissions (.checkpoints (.fineTuning client)))
          ^PermissionDeleteResponse response (.delete svc p)]
      (impl/sdk-object->clj response))))

(defn- ->grader [{:keys [type name input reference operation evaluation-metric]}]
  (case (keyword type)
    :string-check
    (-> (StringCheckGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.operation (StringCheckGrader$Operation/of (impl/enum-name operation))) (.build))
    :text-similarity
    (-> (TextSimilarityGrader/builder) (.name ^String name) (.input ^String input)
        (.reference ^String reference)
        (.evaluationMetric (TextSimilarityGrader$EvaluationMetric/of
                            (impl/enum-name evaluation-metric))) (.build))
    (throw (ex-info (str "Unsupported grader type " type)
                    {:openai/error :unsupported-grader-type :type type}))))

(defn run-grader [^OpenAIClient client {:keys [grader model-sample item]}]
  (impl/with-api-errors
    (let [b (GraderRunParams/builder)
          g (->grader grader)
          _ (cond (instance? StringCheckGrader g) (.grader b ^StringCheckGrader g)
                  (instance? TextSimilarityGrader g) (.grader b ^TextSimilarityGrader g))
          _ (.modelSample b ^String model-sample) _ (.item b (JsonValue/from item))
          svc (.graders (.alpha (.fineTuning client)))]
      (impl/sdk-object->clj (.run ^com.openai.services.blocking.finetuning.alpha.GraderService svc
                                  (.build b))))))

(defn validate-grader [^OpenAIClient client {:keys [grader]}]
  (impl/with-api-errors
    (let [b (GraderValidateParams/builder)
          g (->grader grader)
          _ (cond (instance? StringCheckGrader g) (.grader b ^StringCheckGrader g)
                  (instance? TextSimilarityGrader g) (.grader b ^TextSimilarityGrader g))
          svc (.graders (.alpha (.fineTuning client)))]
      (impl/sdk-object->clj
       (.validate ^com.openai.services.blocking.finetuning.alpha.GraderService svc (.build b))))))
