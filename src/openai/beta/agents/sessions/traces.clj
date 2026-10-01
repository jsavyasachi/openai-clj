(ns openai.beta.agents.sessions.traces
  "Clojure wrapper for beta Agent Session Trace operations."
  (:refer-clojure :exclude [list])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.beta.agents.sessions.traces TraceListPage
                                                          TraceListParams
                                                          TraceListParams$Builder
                                                          TraceListParams$Order)
           (com.openai.services.blocking.beta.agents.sessions TraceService)))

(set! *warn-on-reflection* true)

(defn- ->trace-list-params ^TraceListParams
  [session-id {:keys [after limit order]}]
  (when-not session-id (impl/missing-key! :session-id))
  (let [^TraceListParams$Builder b (TraceListParams/builder)]
    (.sessionId b ^String session-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order
      (.order b (TraceListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn list
  "List all traces for an Agent Session, following SDK pagination."
  ([^OpenAIClient client session-id]
   (list client session-id {}))
  ([^OpenAIClient client session-id opts]
   (impl/with-api-errors
     (let [params (->trace-list-params session-id opts)
           ^TraceService service (.. client (beta) (agents) (sessions) (traces))
           ^TraceListPage page (.list service params)]
       (mapv impl/sdk-object->clj (impl/all-pages page))))))
