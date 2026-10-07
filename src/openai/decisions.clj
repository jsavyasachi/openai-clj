(ns openai.decisions
  "Clojure wrapper for the OpenAI Decisions API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.decisions Decision Decision$Answer Decision$Answer$Choice
                                        Decision$Answer$Score Decision$Answer$Choice$Probability
                                        Decision$Answer$Score$Probability Decision$Usage
                                        DecisionChoiceOption DecisionChoiceOption$Builder
                                        DecisionChoiceValue DecisionCreateParams
                                        DecisionCreateParams$Builder DecisionCreateParams$Question
                                        DecisionCreateParams$Question$Predicate
                                        DecisionCreateParams$Question$Predicate$Builder
                                        DecisionCreateParams$Question$Choice
                                        DecisionCreateParams$Question$Choice$Builder
                                        DecisionCreateParams$Question$Score
                                        DecisionCreateParams$Question$Score$Builder
                                        DecisionCreateParams$Question$Score$Level
                                        DecisionCreateParams$Question$Score$Level$Builder
                                        DecisionInputImage DecisionInputImage$Builder
                                        DecisionInputImage$Detail DecisionInputMessage
                                        DecisionInputMessage$Builder DecisionInputMessage$Type
                                        DecisionInputPart DecisionInputText DecisionInputText$Builder)
           (com.openai.services.blocking DecisionService)))

(set! *warn-on-reflection* true)

(defn- ->input-part ^DecisionInputPart [{:keys [type text image-url detail]}]
  (case type
    :input-text
    (let [^DecisionInputText$Builder b (DecisionInputText/builder)]
      (.text b ^String text)
      (.type b (JsonValue/from "input_text"))
      (DecisionInputPart/ofInputText (.build b)))
    :input-image
    (let [^DecisionInputImage$Builder b (DecisionInputImage/builder)]
      (.imageUrl b ^String image-url)
      (.type b (JsonValue/from "input_image"))
      (when detail (.detail b (DecisionInputImage$Detail/of (impl/enum-name detail))))
      (DecisionInputPart/ofInputImage (.build b)))
    (throw (ex-info "Unsupported decision input part"
                    {:openai/error :unsupported-input-part :type type}))))

(defn- ->input-message ^DecisionInputMessage [{:keys [role content]}]
  (let [^DecisionInputMessage$Builder b (DecisionInputMessage/builder)]
    (.role b (JsonValue/from (impl/enum-name role)))
    (.type b DecisionInputMessage$Type/MESSAGE)
    (if (string? content)
      (.content b ^String content)
      (.contentOfParts b ^java.util.List (mapv ->input-part content)))
    (.build b)))

(defn- ->choice-option ^DecisionChoiceOption [{:keys [value description]}]
  (let [^DecisionChoiceOption$Builder b (DecisionChoiceOption/builder)]
    (if (string? value) (.value b ^String value) (.value b (boolean value)))
    (when description (.description b ^String description))
    (.build b)))

(defn- ->question ^DecisionCreateParams$Question [{:keys [type instructions name choices levels]}]
  (case type
    :predicate
    (let [^DecisionCreateParams$Question$Predicate$Builder b
          (DecisionCreateParams$Question$Predicate/builder)]
      (.instructions b ^String instructions) (.type b (JsonValue/from "predicate"))
      (when name (.name b ^String name))
      (DecisionCreateParams$Question/ofPredicate (.build b)))
    :choice
    (let [^DecisionCreateParams$Question$Choice$Builder b
          (DecisionCreateParams$Question$Choice/builder)]
      (.instructions b ^String instructions) (.type b (JsonValue/from "choice"))
      (doseq [choice choices] (.addChoice b (->choice-option choice)))
      (when name (.name b ^String name))
      (DecisionCreateParams$Question/ofChoice (.build b)))
    :score
    (let [^DecisionCreateParams$Question$Score$Builder b
          (DecisionCreateParams$Question$Score/builder)]
      (.instructions b ^String instructions) (.type b (JsonValue/from "score"))
      (doseq [{:keys [label description]} levels]
        (let [^DecisionCreateParams$Question$Score$Level$Builder lb
              (DecisionCreateParams$Question$Score$Level/builder)]
          (.label lb ^String label)
          (when description (.description lb ^String description))
          (.addLevel b (.build lb))))
      (when name (.name b ^String name))
      (DecisionCreateParams$Question/ofScore (.build b)))
    (throw (ex-info "Unsupported decision question"
                    {:openai/error :unsupported-question :type type}))))

(defn- ->create-params ^DecisionCreateParams [{:keys [model input questions safety-identifier]}]
  (when-not model (impl/missing-key! :model))
  (when-not input (impl/missing-key! :input))
  (when-not questions (impl/missing-key! :questions))
  (let [^DecisionCreateParams$Builder b (DecisionCreateParams/builder)]
    (.model b ^String model)
    (if (string? input)
      (.input b ^String input)
      (.inputOfDecisionInputMessages b ^java.util.List (mapv ->input-message input)))
    (doseq [question questions] (.addQuestion b (->question question)))
    (when safety-identifier (.safetyIdentifier b ^String safety-identifier))
    (.build b)))

(defn- choice-value->clj [^DecisionChoiceValue value]
  (if (.isString value) (.asString value) (.asBool value)))

(defn- choice-probability->map [^Decision$Answer$Choice$Probability probability]
  {:value (choice-value->clj (.value probability)) :probability (.probability probability)})

(defn- score-probability->map [^Decision$Answer$Score$Probability probability]
  {:label (.label probability) :value (.value probability) :probability (.probability probability)})

(defn- answer->map [^Decision$Answer answer]
  (cond
    (.isPredicate answer)
    (let [value (.asPredicate answer)]
      (cond-> {:type :predicate :probability (.probability value)}
        (.isPresent (.name value)) (assoc :name (impl/opt-get (.name value)))))
    (.isChoice answer)
    (let [^Decision$Answer$Choice value (.asChoice answer)]
      (cond-> {:type :choice :choice (choice-value->clj (.choice value))
               :confidence (.confidence value)
               :probabilities (mapv choice-probability->map (.probabilities value))}
        (.isPresent (.name value)) (assoc :name (impl/opt-get (.name value)))))
    (.isScore answer)
    (let [^Decision$Answer$Score value (.asScore answer)]
      (cond-> {:type :score :confidence (.confidence value) :score (.score value)
               :probabilities (mapv score-probability->map (.probabilities value))}
        (.isPresent (.name value)) (assoc :name (impl/opt-get (.name value)))))
    (.isRefusal answer)
    (let [value (.asRefusal answer)]
      (cond-> {:type :refusal}
        (.isPresent (.name value)) (assoc :name (impl/opt-get (.name value)))))))

(defn- usage->map [^Decision$Usage usage]
  {:input-tokens (.inputTokens usage)
   :input-tokens-details (impl/sdk-object->clj (.inputTokensDetails usage))
   :output-tokens (.outputTokens usage)
   :output-tokens-details (impl/sdk-object->clj (.outputTokensDetails usage))
   :total-tokens (.totalTokens usage)})

(defn- decision->map [^Decision decision]
  {:model (.model decision)
   :answers (mapv answer->map (.answers decision))
   :usage (usage->map (.usage decision))})

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^DecisionService service (.decisions client)]
      (decision->map (.create service (->create-params req))))))
