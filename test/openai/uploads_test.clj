(ns openai.uploads-test
  (:require [clojure.test :refer [deftest is]] [openai.uploads :as uploads])
  (:import (com.openai.models.uploads UploadCreateParams)))
(set! *warn-on-reflection* true)
(deftest translates-create-params
  (let [^UploadCreateParams p (#'uploads/->create-params
                               {:filename "data.jsonl" :bytes 12
                                :mime-type "application/jsonl" :purpose :fine-tune})]
    (is (= "data.jsonl" (.filename p)))
    (is (= 12 (.bytes p)))
    (is (= "application/jsonl" (.mimeType p)))
    (is (= "fine_tune" (.asString (.purpose p))))))
