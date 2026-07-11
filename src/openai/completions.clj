(ns openai.completions
  "Idiomatic Clojure wrapper over the legacy OpenAI Completions API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http StreamResponse)
           (com.openai.models.completions Completion CompletionChoice
                                             CompletionChoice$FinishReason
                                             CompletionChoice$Logprobs
                                             CompletionCreateParams
                                             CompletionCreateParams$Builder
                                             CompletionCreateParams$LogitBias
                                             CompletionCreateParams$LogitBias$Builder
                                             CompletionUsage)
           (com.openai.services.blocking CompletionService)))

(set! *warn-on-reflection* true)

(defn- ->logit-bias ^CompletionCreateParams$LogitBias [m]
  (let [^CompletionCreateParams$LogitBias$Builder b (CompletionCreateParams$LogitBias/builder)]
    (.additionalProperties b (impl/->json-value-properties m))
    (.build b)))

(defn- ->create-params ^CompletionCreateParams
  [{:keys [model prompt max-tokens temperature top-p n logprobs echo stop
           presence-penalty frequency-penalty best-of logit-bias seed suffix user]}]
  (when-not model (impl/missing-key! :model))
  (when (nil? prompt) (impl/missing-key! :prompt))
  (let [^CompletionCreateParams$Builder b (CompletionCreateParams/builder)]
    (.model b ^String model)
    (if (string? prompt) (.prompt b ^String prompt) (.promptOfArrayOfStrings b prompt))
    (when max-tokens (.maxTokens b (long max-tokens)))
    (when temperature (.temperature b (double temperature)))
    (when top-p (.topP b (double top-p)))
    (when n (.n b (long n)))
    (when logprobs (.logprobs b (long logprobs)))
    (when (some? echo) (.echo b (boolean echo)))
    (when stop (if (string? stop) (.stop b ^String stop) (.stopOfStrings b stop)))
    (when presence-penalty (.presencePenalty b (double presence-penalty)))
    (when frequency-penalty (.frequencyPenalty b (double frequency-penalty)))
    (when best-of (.bestOf b (long best-of)))
    (when logit-bias (.logitBias b (->logit-bias logit-bias)))
    (when seed (.seed b (long seed)))
    (when suffix (.suffix b ^String suffix))
    (when user (.user b ^String user))
    (.build b)))

(defn- usage->map [^CompletionUsage usage]
  {:prompt-tokens (.promptTokens usage) :completion-tokens (.completionTokens usage)
   :total-tokens (.totalTokens usage)})

(defn- logprobs->map [^CompletionChoice$Logprobs p]
  (cond-> {}
    (.isPresent (.textOffset p)) (assoc :text-offset (impl/opt-get (.textOffset p)))
    (.isPresent (.tokenLogprobs p)) (assoc :token-logprobs (impl/opt-get (.tokenLogprobs p)))
    (.isPresent (.tokens p)) (assoc :tokens (impl/opt-get (.tokens p)))
    (.isPresent (.topLogprobs p)) (assoc :top-logprobs (impl/opt-get (.topLogprobs p)))))

(defn- choice->map [^CompletionChoice choice]
  (cond-> {:index (.index choice) :text (.text choice)
           :finish-reason (impl/->keyword (.asString ^CompletionChoice$FinishReason (.finishReason choice)))}
    (.isPresent (.logprobs choice))
    (assoc :logprobs (logprobs->map (impl/opt-get (.logprobs choice))))))

(defn- completion->map [^Completion completion]
  (let [choices (mapv choice->map (.choices completion))]
    (cond-> {:id (.id completion) :model (.model completion) :created (.created completion)
             :choices choices :text (or (:text (first choices)) "")}
      (.isPresent (.usage completion))
      (assoc :usage (usage->map (impl/opt-get (.usage completion)))))))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^CompletionService svc (.completions client)]
      (completion->map (.create svc (->create-params req))))))

(defn- drain-stream ^String [^StreamResponse sr on-event]
  (let [sb (StringBuilder.)]
    (doseq [^Completion chunk (iterator-seq (.iterator (.stream sr)))]
      (let [m (completion->map chunk)]
        (doseq [choice (:choices m)] (.append sb ^String (:text choice)))
        (when on-event (on-event m))))
    (str sb)))

(defn create-streaming ^String [^OpenAIClient client req on-event]
  (impl/with-api-errors
    (let [^CompletionService svc (.completions client)]
      (with-open [^StreamResponse sr (.createStreaming svc (->create-params req))]
        (drain-stream sr on-event)))))
