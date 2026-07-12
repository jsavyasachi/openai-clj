(ns openai.fine-tuning-test
  (:require [clojure.test :refer [deftest is]]
            [openai.fine-tuning :as fine-tuning])
  (:import (com.openai.core JsonValue)
           (com.openai.models.finetuning.alpha.graders GraderRunResponse
                                                          GraderRunResponse$Metadata
                                                          GraderRunResponse$Metadata$Errors
                                                          GraderRunResponse$Metadata$Scores
                                                          GraderRunResponse$ModelGraderTokenUsagePerModel
                                                          GraderRunResponse$SubRewards
                                                          GraderValidateResponse
                                                          GraderValidateResponse$Grader)
           (com.openai.models.finetuning.checkpoints.permissions PermissionCreateResponse
                                                                   PermissionDeleteResponse
                                                                   PermissionListResponse)
           (com.openai.models.finetuning.jobs FineTuningJob FineTuningJob$Builder
                                                FineTuningJob$Error
                                                FineTuningJob$Hyperparameters
                                                FineTuningJob$Metadata
                                                FineTuningJob$Status
                                                FineTuningJobEvent FineTuningJobEvent$Level
                                                FineTuningJobEvent$Type
                                                JobCreateParams JobListParams)
           (com.openai.models.finetuning.jobs.checkpoints FineTuningJobCheckpoint
                                                           FineTuningJobCheckpoint$Metrics)
           (com.openai.models.graders.gradermodels StringCheckGrader
                                                    StringCheckGrader$Operation)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))

(deftest translates-job-create-params
  (let [^JobCreateParams p (#'fine-tuning/->job-create-params
                             {:model "gpt-4.1-mini" :training-file "file-train"
                              :validation-file "file-valid" :suffix "demo" :seed 42
                              :metadata {:team "sdk"}
                              :hyperparameters {:n-epochs 3}
                              :method {:type "supervised"}})
        extras (._additionalBodyProperties p)]
    (is (= "gpt-4.1-mini" (.asString (.model p))))
    (is (= "file-train" (.trainingFile p)))
    (is (= "file-valid" (opt (.validationFile p))))
    (is (= "demo" (opt (.suffix p))))
    (is (= 42 (opt (.seed p))))
    (is (= #{"metadata" "hyperparameters" "method"} (set (keys extras))))))

(deftest translates-job-list-params
  (let [^JobListParams p (#'fine-tuning/->job-list-params {:after "job_1" :limit 25})]
    (is (= "job_1" (opt (.after p))))
    (is (= 25 (opt (.limit p))))))

(deftest converts-fine-tuning-job-with-present-only-optionals
  (let [^java.util.Optional absent (java.util.Optional/empty)
        hyperparameters (-> (FineTuningJob$Hyperparameters/builder)
                            (.batchSize 4) (.learningRateMultiplier 0.1)
                            (.nEpochs 3) (.build))
        error (-> (FineTuningJob$Error/builder) (.code "invalid_file")
                  (.message "Bad training file") (.param "training_file") (.build))
        ^FineTuningJob$Builder base (doto (FineTuningJob/builder)
                                     (.id "ftjob_1") (.createdAt 10)
                                     (.hyperparameters hyperparameters) (.model "gpt-4.1-mini")
                                     (.object_ (JsonValue/from "fine_tuning.job"))
                                     (.organizationId "org_1")
                                     (.resultFiles (java.util.ArrayList.)) (.seed 42)
                                     (.status (FineTuningJob$Status/of "running"))
                                     (.trainingFile "file-train")
                                     (.error absent) (.fineTunedModel absent)
                                     (.finishedAt absent) (.validationFile absent)
                                     (.trainedTokens absent) (.metadata absent))
        minimal (#'fine-tuning/job->map (.build base))
        metadata (-> (FineTuningJob$Metadata/builder)
                     (.putAdditionalProperty "team_name" (JsonValue/from "sdk"))
                     (.putAdditionalProperty "attempt_count" (JsonValue/from 2))
                     (.build))
        full (#'fine-tuning/job->map
              (.build
               (doto base (.resultFiles (java.util.ArrayList. ["file-result"]))
                 (.finishedAt 20) (.fineTunedModel "ft:gpt-4.1-mini")
                 (.validationFile "file-valid") (.trainedTokens 100) (.error error)
                 (.metadata metadata))))]
    (is (= {:id "ftjob_1" :model "gpt-4.1-mini" :status :running
            :created-at 10 :training-file "file-train" :result-files [] :seed 42}
           (dissoc minimal :hyperparameters)))
    (is (not (contains? minimal :fine-tuned-model)))
    (is (= ["file-result"] (:result-files full)))
    (is (= [20 "ft:gpt-4.1-mini" "file-valid" 100]
           ((juxt :finished-at :fine-tuned-model :validation-file :trained-tokens) full)))
    (is (= {:batch-size 4 :learning-rate-multiplier 0.1 :n-epochs 3}
           (:hyperparameters minimal)))
    (is (= {:code "invalid_file" :message "Bad training file"
            :param "training_file"}
           (:error full)))
    (is (= {:team-name "sdk" :attempt-count 2} (:metadata full)))))

(deftest converts-fine-tuning-event
  (let [convert (some-> (ns-resolve 'openai.fine-tuning 'event->map) deref)
        event (-> (FineTuningJobEvent/builder)
                  (.id "ftevent_1") (.createdAt 11)
                  (.level (FineTuningJobEvent$Level/of "info"))
                  (.message "Training started")
                  (.object_ (JsonValue/from "fine_tuning.job.event"))
                  (.data (JsonValue/from {"step_count" 2}))
                  (.type (FineTuningJobEvent$Type/of "message")) (.build))]
    (is (= {:id "ftevent_1" :created-at 11 :level :info
            :message "Training started" :data {:step-count 2} :type :message}
           (when convert (convert event))))))

(deftest converts-fine-tuning-checkpoint-with-present-only-metrics
  (let [convert (some-> (ns-resolve 'openai.fine-tuning 'checkpoint->map) deref)
        metrics (-> (FineTuningJobCheckpoint$Metrics/builder)
                    (.trainLoss 0.25) (.validMeanTokenAccuracy 0.9) (.build))
        checkpoint (-> (FineTuningJobCheckpoint/builder)
                       (.id "ftckpt_1") (.createdAt 12)
                       (.fineTunedModelCheckpoint "ft:model:ckpt-step-10")
                       (.fineTuningJobId "ftjob_1") (.metrics metrics)
                       (.object_ (JsonValue/from "fine_tuning.job.checkpoint"))
                       (.stepNumber 10) (.build))]
    (is (= {:id "ftckpt_1" :created-at 12
            :fine-tuned-model-checkpoint "ft:model:ckpt-step-10"
            :fine-tuning-job-id "ftjob_1" :step-number 10
            :metrics {:train-loss 0.25 :valid-mean-token-accuracy 0.9}}
           (when convert (convert checkpoint))))))

(deftest converts-checkpoint-permission-responses
  (let [create-convert (some-> (ns-resolve 'openai.fine-tuning 'permission-create->map) deref)
        list-convert (some-> (ns-resolve 'openai.fine-tuning 'permission-list->map) deref)
        delete-convert (some-> (ns-resolve 'openai.fine-tuning 'permission-delete->map) deref)
        create-response (-> (PermissionCreateResponse/builder)
                            (.id "perm_1") (.createdAt 13)
                            (.object_ (JsonValue/from "checkpoint.permission"))
                            (.projectId "proj_1") (.build))
        list-response (-> (PermissionListResponse/builder)
                          (.id "perm_2") (.createdAt 14)
                          (.object_ (JsonValue/from "checkpoint.permission"))
                          (.projectId "proj_2") (.build))
        delete-response (-> (PermissionDeleteResponse/builder)
                            (.id "perm_1") (.deleted true)
                            (.object_ (JsonValue/from "checkpoint.permission.deleted"))
                            (.build))]
    (is (= {:id "perm_1" :created-at 13 :project-id "proj_1"}
           (when create-convert (create-convert create-response))))
    (is (= {:id "perm_2" :created-at 14 :project-id "proj_2"}
           (when list-convert (list-convert list-response))))
    (is (= {:id "perm_1" :deleted true}
           (when delete-convert (delete-convert delete-response))))))

(deftest converts-grader-run-and-validate-responses
  (let [run-convert (some-> (ns-resolve 'openai.fine-tuning 'grader-run->map) deref)
        validate-convert (some-> (ns-resolve 'openai.fine-tuning 'grader-validate->map) deref)
        errors (-> (GraderRunResponse$Metadata$Errors/builder)
                   (.formulaParseError false) (.invalidVariableError false)
                   (.modelGraderParseError false) (.modelGraderRefusalError false)
                   (.modelGraderServerError true) (.modelGraderServerErrorDetails "timeout")
                   (.otherError false) (.pythonGraderRuntimeError false)
                   (.pythonGraderRuntimeErrorDetails (java.util.Optional/empty))
                   (.pythonGraderServerError false)
                   (.pythonGraderServerErrorType (java.util.Optional/empty))
                   (.sampleParseError false)
                   (.truncatedObservationError false) (.unresponsiveRewardError false)
                   (.build))
        scores (-> (GraderRunResponse$Metadata$Scores/builder)
                   (.putAdditionalProperty "string_check" (JsonValue/from 1.0)) (.build))
        metadata (-> (GraderRunResponse$Metadata/builder)
                     (.errors errors) (.executionTime 0.4) (.name "exact")
                     (.sampledModelName "gpt-4.1-mini") (.scores scores)
                     (.tokenUsage 20) (.type "string_check") (.build))
        usage (-> (GraderRunResponse$ModelGraderTokenUsagePerModel/builder)
                  (.putAdditionalProperty "gpt-4.1-mini" (JsonValue/from 20)) (.build))
        sub-rewards (-> (GraderRunResponse$SubRewards/builder)
                        (.putAdditionalProperty "exact_match" (JsonValue/from 1.0)) (.build))
        run-response (-> (GraderRunResponse/builder) (.metadata metadata)
                         (.modelGraderTokenUsagePerModel usage) (.reward 1.0)
                         (.subRewards sub-rewards) (.build))
        grader (-> (StringCheckGrader/builder) (.name "exact") (.input "{{sample}}")
                   (.reference "yes") (.operation (StringCheckGrader$Operation/of "eq"))
                   (.build))
        validate-response (-> (GraderValidateResponse/builder)
                              (.grader (GraderValidateResponse$Grader/ofStringCheck grader))
                              (.build))]
    (is (= {:reward 1.0
            :metadata {:errors {:formula-parse-error false :invalid-variable-error false
                                :model-grader-parse-error false
                                :model-grader-refusal-error false
                                :model-grader-server-error true
                                :model-grader-server-error-details "timeout"
                                :other-error false :python-grader-runtime-error false
                                :python-grader-server-error false :sample-parse-error false
                                :truncated-observation-error false
                                :unresponsive-reward-error false}
                       :execution-time 0.4 :name "exact"
                       :sampled-model-name "gpt-4.1-mini"
                       :scores {:string-check 1.0} :token-usage 20
                       :type "string_check"}
            :model-grader-token-usage-per-model {:gpt-4.1-mini 20}
            :sub-rewards {:exact-match 1.0}}
           (when run-convert (run-convert run-response))))
    (is (= {:grader {:type :string-check :name "exact" :input "{{sample}}"
                     :reference "yes" :operation :eq}}
           (when validate-convert (validate-convert validate-response))))))
