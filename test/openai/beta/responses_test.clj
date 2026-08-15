(ns openai.beta.responses-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.impl :as impl]
            [openai.beta.responses :as responses])
  (:import (com.openai.core JsonValue)
           (com.openai.models.beta.responses BetaCompactedResponse
                                             BetaResponse
                                             BetaResponseStatus
                                             BetaResponseStreamEvent
                                             BetaResponseTextDeltaEvent
                                             BetaResponseUsage
                                             BetaResponseUsage$InputTokensDetails
                                             BetaResponseUsage$OutputTokensDetails
                                             BetaToolChoiceOptions
                                             ResponseCreateParams
                                             ResponseCreateParams$Beta)
           (com.openai.models.beta.responses.inputitems InputItemListParams)
           (com.openai.models.beta.responses.inputtokens InputTokenCountResponse)))

(defn- empty-usage []
  (-> (BetaResponseUsage/builder)
      (.inputTokens 10)
      (.inputTokensDetails
       (-> (BetaResponseUsage$InputTokensDetails/builder)
           (.cacheWriteTokens 0)
           (.cachedTokens 0)
           (.build)))
      (.outputTokens 20)
      (.outputTokensDetails
       (-> (BetaResponseUsage$OutputTokensDetails/builder)
           (.reasoningTokens 0)
           (.build)))
      (.totalTokens 30)
      (.build)))

(defn- beta-response []
  (let [^java.util.Optional empty (java.util.Optional/empty)]
    (-> (BetaResponse/builder)
        (.id "resp_beta")
        (.createdAt 1234.5)
        (.error empty)
        (.incompleteDetails empty)
        (.instructions empty)
        (.metadata empty)
        (.model "gpt-beta")
        (.object_ (JsonValue/from "response"))
        (.output [])
        (.parallelToolCalls false)
        (.temperature empty)
        (.toolChoice BetaToolChoiceOptions/AUTO)
        (.tools [])
        (.topP empty)
        (.background empty)
        (.completedAt empty)
        (.conversation empty)
        (.maxOutputTokens empty)
        (.maxToolCalls empty)
        (.previousResponseId empty)
        (.prompt empty)
        (.promptCacheKey empty)
        (.promptCacheRetention empty)
        (.reasoning empty)
        (.safetyIdentifier empty)
        (.serviceTier empty)
        (.status BetaResponseStatus/COMPLETED)
        (.truncation empty)
        (.usage (empty-usage))
        (.build))))

(deftest translates-beta-create-params
  (let [p (#'responses/->params
           {:model "gpt-beta"
            :input "hello"
            :betas [:responses-multi-agent-v1]
            :multi-agent {:enabled true :max-concurrent-subagents 3}})]
    (is (instance? com.openai.models.beta.responses.ResponseCreateParams p))
    (is (= "gpt-beta" (.asString (impl/opt-get (.model p)))))
    (is (= "hello" (.asText (impl/opt-get (.input p)))))
    (is (= ["responses_multi_agent_v1"]
           (mapv #(.asString ^ResponseCreateParams$Beta %) (impl/opt-get (.betas p)))))
    (let [multi-agent (impl/opt-get (.multiAgent p))]
      (is (true? (.enabled multi-agent)))
      (is (= 3 (impl/opt-get (.maxConcurrentSubagents multi-agent)))))
    (let [^ResponseCreateParams p (#'responses/->params
                                   {:model "gpt-beta"
                                    :input [{:role :user :content "hello"}]})
          input (impl/opt-get (.input p))]
      (is (.isBetaResponse input))
      (is (= 1 (count (.asBetaResponse input)))))))

(deftest translates-beta-operation-params
  (let [retrieve (#'responses/->retrieve-params "resp_1" {:include [:reasoning-encrypted-content]
                                                            :include-obfuscation true
                                                            :starting-after 2})
        cancel (#'responses/->cancel-params "resp_1")
        delete (#'responses/->delete-params "resp_1")
        compact (#'responses/->compact-params {:model "gpt-beta"
                                               :previous-response-id "resp_1"})
        items (#'responses/->input-item-list-params "resp_1" {:after "item_1"
                                                               :limit 5
                                                               :order :desc})]
    (is (= "resp_1" (impl/opt-get (.responseId retrieve))))
    (is (true? (impl/opt-get (.includeObfuscation retrieve))))
    (is (= 2 (impl/opt-get (.startingAfter retrieve))))
    (is (= "resp_1" (impl/opt-get (.responseId cancel))))
    (is (= "resp_1" (impl/opt-get (.responseId delete))))
    (is (= "resp_1" (impl/opt-get (.previousResponseId compact))))
    (is (= "item_1" (impl/opt-get (.after items))))
    (is (= 5 (impl/opt-get (.limit items))))
    (is (= "desc" (.asString (impl/opt-get (.order items)))))))

(deftest maps-beta-responses
  (is (= {:id "resp_beta"
          :model "gpt-beta"
          :output []
          :text ""
          :created-at 1234.5
          :status :completed
          :usage {:input-tokens 10
                  :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
                  :output-tokens 20
                  :output-tokens-details {:reasoning-tokens 0}
                  :total-tokens 30}}
         (#'responses/beta-response->map (beta-response)))))

(deftest maps-beta-compacted-response
  (let [r (-> (BetaCompactedResponse/builder)
              (.id "resp_compact")
              (.createdAt 1234)
              (.object_ (JsonValue/from "response.compaction"))
              (.output [])
              (.usage (empty-usage))
              (.build))]
    (is (= {:id "resp_compact" :output [] :text ""
            :usage {:input-tokens 10
                    :input-tokens-details {:cache-write-tokens 0 :cached-tokens 0}
                    :output-tokens 20
                    :output-tokens-details {:reasoning-tokens 0}
                    :total-tokens 30}
            :created-at 1234}
           (#'responses/beta-compacted-response->map r)))))

(deftest maps-beta-token-count-response
  (let [r (-> (InputTokenCountResponse/builder)
              (.inputTokens 42)
              (.object_ (JsonValue/from "response.input_tokens"))
              (.build))]
    (is (= {:input-tokens 42} (#'responses/input-token-count-response->map r)))))

(deftest maps-beta-stream-events
  (let [event (BetaResponseStreamEvent/ofResponseOutputTextDelta
               (-> (BetaResponseTextDeltaEvent/builder)
                   (.contentIndex 0)
                   (.delta "Hel")
                   (.itemId "msg_1")
                   (.logprobs [])
                   (.outputIndex 0)
                   (.sequenceNumber 1)
                   (.build)))]
    (is (= {:type :output-text-delta
            :delta "Hel"
            :item-id "msg_1"
            :output-index 0}
           (#'responses/event->map event)))))
