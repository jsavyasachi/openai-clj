(ns openai.fine-tuning-test
  (:require [clojure.test :refer [deftest is]] [openai.fine-tuning :as fine-tuning])
  (:import (com.openai.models.finetuning.jobs JobCreateParams)))
(set! *warn-on-reflection* true)
(deftest translates-job-create-params
  (let [^JobCreateParams p (#'fine-tuning/->job-create-params
                             {:model "gpt-4.1-mini" :training-file "file-train"
                              :validation-file "file-valid" :suffix "demo" :seed 42})]
    (is (= "gpt-4.1-mini" (.asString (.model p))))
    (is (= "file-train" (.trainingFile p)))
    (is (= "file-valid" (.get (.validationFile p))))
    (is (= 42 (.get (.seed p))))))
