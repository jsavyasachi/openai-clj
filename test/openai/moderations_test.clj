(ns openai.moderations-test
  (:require [clojure.test :refer [deftest is]]
            [openai.moderations])
  (:import (com.openai.models.moderations Moderation Moderation$Builder Moderation$Categories
                                             Moderation$CategoryScores
                                             Moderation$CategoryAppliedInputTypes
                                             Moderation$CategoryAppliedInputTypes$Builder
                                             ModerationCreateParams
                                             ModerationCreateParams$Input
                                             ModerationCreateResponse)))

(set! *warn-on-reflection* true)

(deftest translates-input
  (let [^ModerationCreateParams string-params (#'openai.moderations/->create-params {:input "hello"})
        ^ModerationCreateParams vector-params (#'openai.moderations/->create-params {:input ["hello" "world"]})
        ^ModerationCreateParams$Input string-input (.input string-params)
        ^ModerationCreateParams$Input vector-input (.input vector-params)]
    (is (= "hello" (.asString string-input)))
    (is (= ["hello" "world"] (.asStrings vector-input)))))

(deftest converts-response
  (let [categories (-> (Moderation$Categories/builder)
                       (.harassment true) (.harassmentThreatening false)
                       (.hate false) (.hateThreatening false) (.illicit false) (.illicitViolent false)
                       (.selfHarm false) (.selfHarmInstructions false) (.selfHarmIntent false)
                       (.sexual false) (.sexualMinors false)
                       (.violence false) (.violenceGraphic false)
                       (.build))
        scores (-> (Moderation$CategoryScores/builder)
                   (.harassment 0.9) (.harassmentThreatening 0.0)
                   (.hate 0.0) (.hateThreatening 0.0)
                   (.illicit 0.0) (.illicitViolent 0.0)
                   (.selfHarm 0.0) (.selfHarmInstructions 0.0) (.selfHarmIntent 0.0)
                   (.sexual 0.0) (.sexualMinors 0.0)
                   (.violence 0.0) (.violenceGraphic 0.0)
                   (.build))
        ^java.util.List empty (java.util.Collections/emptyList)
        ^Moderation$CategoryAppliedInputTypes$Builder ab (Moderation$CategoryAppliedInputTypes/builder)
        applied (do (.harassment ab empty) (.harassmentThreatening ab empty)
                    (.hate ab empty) (.hateThreatening ab empty) (.illicit ab empty) (.illicitViolent ab empty)
                    (.selfHarm ab empty) (.selfHarmInstructions ab empty) (.selfHarmIntent ab empty)
                    (.sexual ab empty) (.sexualMinors ab empty) (.violence ab empty) (.violenceGraphic ab empty)
                    (.build ab))
        ^Moderation$Builder mb (Moderation/builder)
        result (do (.categories mb categories) (.categoryAppliedInputTypes mb applied)
                   (.categoryScores mb scores) (.flagged mb true) (.build mb))
        response (-> (ModerationCreateResponse/builder) (.id "modr-1")
                     (.model "omni-moderation-latest") (.addResult result) (.build))
        m (#'openai.moderations/response->map response)]
    (is (= "modr-1" (:id m)))
    (is (= true (get-in m [:results 0 :flagged])))
    (is (= true (get-in m [:results 0 :categories :harassment])))
    (is (= 0.9 (get-in m [:results 0 :category-scores :harassment])))))
