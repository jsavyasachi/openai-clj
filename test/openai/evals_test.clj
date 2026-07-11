(ns openai.evals-test
  (:require [clojure.test :refer [deftest is]] [openai.evals :as evals])
  (:import (com.openai.models.evals EvalCreateParams EvalCreateParams$TestingCriterion)))
(set! *warn-on-reflection* true)
(deftest translates-custom-eval
  (let [^EvalCreateParams p
        (#'evals/->create-params
         {:name "quality" :data-source-config {:type :custom :item-schema {:type "object"}}
          :testing-criteria [{:type :string-check :name "exact" :input "{{sample.output_text}}"
                              :reference "{{item.answer}}" :operation :eq}]})]
    (is (= "quality" (some-> (.name p) (.get))))
    (is (.isCustom (.dataSourceConfig p)))
    (is (.isStringCheck ^EvalCreateParams$TestingCriterion
                        (first (.testingCriteria p))))))

(deftest exposes-run-and-output-item-prefix-apis
  (doseq [sym '[run-create run-retrieve run-list run-cancel run-delete
                output-item-list output-item-retrieve]]
    (is (fn? (some-> (ns-resolve 'openai.evals sym) deref)))))
