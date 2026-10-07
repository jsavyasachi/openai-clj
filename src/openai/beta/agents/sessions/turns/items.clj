(ns openai.beta.agents.sessions.turns.items
  "Clojure wrapper for beta Agent Session Turn Item operations."
  (:refer-clojure :exclude [list])
  (:require [openai.beta.agents.sessions.items]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.sessions.turns.items ItemListPage ItemListParams
                                                               ItemListParams$Builder ItemListParams$Order)
           (com.openai.services.blocking.beta.agents.sessions.turns ItemService)))

(set! *warn-on-reflection* true)

(defn- ->item-list-params ^ItemListParams [session-id turn-id {:keys [after limit order]}]
  (when-not session-id (impl/missing-key! :session-id))
  (when-not turn-id (impl/missing-key! :turn-id))
  (let [^ItemListParams$Builder b (ItemListParams/builder)]
    (.sessionId b ^String session-id) (.turnId b ^String turn-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (ItemListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn list
  "List all items for an Agent Session turn, following SDK pagination."
  ([^OpenAIClient client session-id turn-id] (list client session-id turn-id {}))
  ([^OpenAIClient client session-id turn-id opts]
   (impl/with-api-errors
     (let [^ItemService service (.. client (beta) (agents) (sessions) (turns) (items))
           ^ItemListPage page (.list service (->item-list-params session-id turn-id opts))]
       (mapv #(@#'openai.beta.agents.sessions.items/item->map %) (impl/all-pages page))))))
