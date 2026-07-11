(ns openai.moderations
  "Idiomatic Clojure wrapper over the OpenAI Moderations API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.moderations Moderation Moderation$Categories
                                             Moderation$CategoryAppliedInputTypes
                                             Moderation$CategoryScores
                                             ModerationCreateParams
                                             ModerationCreateParams$Builder
                                             ModerationCreateResponse
                                             ModerationImageUrlInput
                                             ModerationImageUrlInput$Builder
                                             ModerationImageUrlInput$ImageUrl
                                             ModerationImageUrlInput$ImageUrl$Builder
                                             ModerationMultiModalInput
                                             ModerationTextInput
                                             ModerationTextInput$Builder)
           (com.openai.services.blocking ModerationService)))

(set! *warn-on-reflection* true)

(defn- ->multimodal-part ^ModerationMultiModalInput [{:keys [type text image-url]}]
  (case type
    :text (let [^ModerationTextInput$Builder b (ModerationTextInput/builder)]
            (.text b ^String text) (.type b (JsonValue/from "text"))
            (ModerationMultiModalInput/ofText (.build b)))
    :image-url
    (let [url (if (map? image-url) (:url image-url) image-url)
          ^ModerationImageUrlInput$ImageUrl$Builder ub (ModerationImageUrlInput$ImageUrl/builder)
          ^ModerationImageUrlInput$Builder b (ModerationImageUrlInput/builder)]
      (.url ub ^String url) (.imageUrl b (.build ub))
      (.type b (JsonValue/from "image_url"))
      (ModerationMultiModalInput/ofImageUrl (.build b)))
    (throw (ex-info "Unsupported moderation input part"
                    {:openai/error :unsupported-input-part :type type}))))

(defn- ->create-params ^ModerationCreateParams [{:keys [input model]}]
  (when (nil? input) (impl/missing-key! :input))
  (let [^ModerationCreateParams$Builder b (ModerationCreateParams/builder)]
    (cond
      (string? input) (.input b ^String input)
      (and (vector? input) (every? string? input)) (.inputOfStrings b input)
      (vector? input) (.inputOfModerationMultiModalArray b (mapv ->multimodal-part input))
      :else (throw (ex-info "Unsupported moderation input"
                            {:openai/error :unsupported-input :input input})))
    (when model (.model b ^String model))
    (.build b)))

(defn- categories->map [^Moderation$Categories c]
  (cond-> {:harassment (.harassment c)
           :harassment-threatening (.harassmentThreatening c)
           :hate (.hate c) :hate-threatening (.hateThreatening c)
           :self-harm (.selfHarm c) :self-harm-instructions (.selfHarmInstructions c)
           :self-harm-intent (.selfHarmIntent c) :sexual (.sexual c)
           :sexual-minors (.sexualMinors c) :violence (.violence c)
           :violence-graphic (.violenceGraphic c)}
    (.isPresent (.illicit c)) (assoc :illicit (impl/opt-get (.illicit c)))
    (.isPresent (.illicitViolent c)) (assoc :illicit-violent (impl/opt-get (.illicitViolent c)))))

(defn- scores->map [^Moderation$CategoryScores s]
  {:harassment (.harassment s) :harassment-threatening (.harassmentThreatening s)
   :hate (.hate s) :hate-threatening (.hateThreatening s)
   :illicit (.illicit s) :illicit-violent (.illicitViolent s)
   :self-harm (.selfHarm s) :self-harm-instructions (.selfHarmInstructions s)
   :self-harm-intent (.selfHarmIntent s) :sexual (.sexual s)
   :sexual-minors (.sexualMinors s) :violence (.violence s)
   :violence-graphic (.violenceGraphic s)})

(defn- applied-types->map [^Moderation$CategoryAppliedInputTypes a]
  (let [convert #(mapv impl/->keyword %)]
    {:harassment (convert (.harassment a))
     :harassment-threatening (convert (.harassmentThreatening a))
     :hate (convert (.hate a)) :hate-threatening (convert (.hateThreatening a))
     :illicit (convert (.illicit a)) :illicit-violent (convert (.illicitViolent a))
     :self-harm (convert (.selfHarm a))
     :self-harm-instructions (convert (.selfHarmInstructions a))
     :self-harm-intent (convert (.selfHarmIntent a))
     :sexual (convert (.sexual a)) :sexual-minors (convert (.sexualMinors a))
     :violence (convert (.violence a)) :violence-graphic (convert (.violenceGraphic a))}))

(defn- moderation->map [^Moderation m]
  {:flagged (.flagged m) :categories (categories->map (.categories m))
   :category-scores (scores->map (.categoryScores m))
   :category-applied-input-types (applied-types->map (.categoryAppliedInputTypes m))})

(defn- response->map [^ModerationCreateResponse response]
  {:id (.id response) :model (.model response)
   :results (mapv moderation->map (.results response))})

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ModerationService svc (.moderations client)]
      (response->map (.create svc (->create-params req))))))
