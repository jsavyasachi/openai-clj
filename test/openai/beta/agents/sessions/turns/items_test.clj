(ns openai.beta.agents.sessions.turns.items-test
  (:require [clojure.test :refer [deftest is]]
            [openai.beta.agents.sessions.turns.items :as turn-items]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents AgentOutputItemStatus AgentSessionItem
                                           AgentSessionMessage AgentSessionMessage$Role)
           (com.openai.models.beta.agents.sessions.turns.items ItemListPage ItemListPageResponse
                                                               ItemListParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions TurnService)
           (com.openai.services.blocking.beta.agents.sessions.turns ItemService)))

(deftest builds-turn-item-list-params
  (let [^ItemListParams params
        (#'openai.beta.agents.sessions.turns.items/->item-list-params
         "sess_1" "turn_1" {:after "item_1" :limit 10 :order :desc})]
    (is (= "sess_1" (.sessionId params)))
    (is (= "turn_1" (impl/opt-get (.turnId params))))
    (is (= "item_1" (impl/opt-get (.after params))))
    (is (= 10 (impl/opt-get (.limit params))))
    (is (= "desc" (.asString (impl/opt-get (.order params)))))))

(defn- turn-item [id text]
  (AgentSessionItem/ofMessage
   (-> (AgentSessionMessage/builder)
       (.id ^String id)
       (.addInputTextContent ^String text)
       (.phase (java.util.Optional/empty))
       (.role AgentSessionMessage$Role/USER)
       (.status AgentOutputItemStatus/COMPLETED)
       (.turnId "turn_1")
       (.build))))

(defn- turn-item-page [^ItemService service ^ItemListParams params items has-more]
  (let [^AgentSessionItem first-item (first items)
        ^AgentSessionItem last-item (last items)
        response (-> (ItemListPageResponse/builder)
                     (.data ^java.util.List items)
                     (.firstId (if first-item (-> first-item .asMessage .id impl/opt-get)
                                   (java.util.Optional/empty)))
                     (.hasMore (boolean has-more))
                     (.lastId (if last-item (-> last-item .asMessage .id impl/opt-get)
                                  (java.util.Optional/empty)))
                     (.build))]
    (-> (ItemListPage/builder)
        (.service service)
        (.params params)
        (.response response)
        (.build))))

(deftest lists-turn-items-across-pages
  (let [captured (atom [])
        service-ref (atom nil)
        service (proxy [ItemService] []
                  (list [params]
                    (swap! captured conj params)
                    (if (impl/opt-get (.after ^ItemListParams params))
                      (turn-item-page @service-ref params [(turn-item "item_2" "second")] false)
                      (turn-item-page @service-ref params [(turn-item "item_1" "first")] true))))
        _ (reset! service-ref service)
        turns (proxy [TurnService] [] (items [] service))
        sessions (proxy [SessionService] [] (turns [] turns))
        agents (proxy [AgentService] [] (sessions [] sessions))
        beta (proxy [BetaService] [] (agents [] agents))
        client (proxy [OpenAIClient] [] (beta [] beta))]
    (is (= [{:role :user :content [{:text "first" :type :input-text}] :phase nil
             :type :message :status :completed :id "item_1" :turn-id "turn_1"}
            {:role :user :content [{:text "second" :type :input-text}] :phase nil
             :type :message :status :completed :id "item_2" :turn-id "turn_1"}]
           (turn-items/list client "sess_1" "turn_1")))
    (is (= ["sess_1" "sess_1"]
           (mapv #(.sessionId ^ItemListParams %) @captured)))
    (is (= ["turn_1" "turn_1"]
           (mapv #(impl/opt-get (.turnId ^ItemListParams %)) @captured)))))

(deftest requires-session-and-turn-ids
  (let [error-data (fn [session-id turn-id]
                     (try
                       (#'turn-items/->item-list-params session-id turn-id {})
                       nil
                       (catch clojure.lang.ExceptionInfo e (ex-data e))))]
    (is (= {:openai/error :missing-key :key :session-id}
           (error-data nil "turn_1")))
    (is (= {:openai/error :missing-key :key :turn-id}
           (error-data "sess_1" nil)))))
