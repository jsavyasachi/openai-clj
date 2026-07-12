(ns openai.fine-tuning-test
  (:require [clojure.test :refer [deftest is]]
            [openai.fine-tuning :as fine-tuning])
  (:import (com.openai.core JsonValue)
           (com.openai.models.finetuning.jobs FineTuningJob FineTuningJob$Builder
                                                FineTuningJob$Error
                                                FineTuningJob$Hyperparameters
                                                FineTuningJob$Status
                                                JobCreateParams JobListParams)))

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
        hyperparameters (-> (FineTuningJob$Hyperparameters/builder) (.nEpochs 3) (.build))
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
        full (#'fine-tuning/job->map
              (.build
               (doto base (.resultFiles (java.util.ArrayList. ["file-result"]))
                 (.finishedAt 20) (.fineTunedModel "ft:gpt-4.1-mini")
                 (.validationFile "file-valid") (.trainedTokens 100) (.error error))))]
    (is (= {:id "ftjob_1" :model "gpt-4.1-mini" :status :running
            :created-at 10 :training-file "file-train" :result-files [] :seed 42}
           (dissoc minimal :hyperparameters)))
    (is (not (contains? minimal :fine-tuned-model)))
    (is (= ["file-result"] (:result-files full)))
    (is (= [20 "ft:gpt-4.1-mini" "file-valid" 100]
           ((juxt :finished-at :fine-tuned-model :validation-file :trained-tokens) full)))
    (is (map? (:error full)))))
