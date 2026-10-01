(ns openai.beta.agents.sessions.traces-test
  (:require [clojure.test :refer [deftest is]]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue)
           (com.openai.models.beta.agents.sessions.traces SessionTrace
                                                          SessionTrace$Otlp
                                                          TraceListPage
                                                          TraceListPageResponse
                                                          TraceListParams)
           (com.openai.services.blocking BetaService)
           (com.openai.services.blocking.beta AgentService)
           (com.openai.services.blocking.beta.agents SessionService)
           (com.openai.services.blocking.beta.agents.sessions TraceService)))

(defn- api [sym]
  (try
    (require 'openai.beta.agents.sessions.traces)
    (some-> (ns-resolve 'openai.beta.agents.sessions.traces sym) deref)
    (catch java.io.FileNotFoundException _ nil)))

(deftest exposes-session-trace-listing
  (if-let [list-traces (api 'list)]
    (let [calls (atom [])
          service-ref (atom nil)
          trace (-> (SessionTrace/builder)
                    (.id "trace_1") (.createdAt 1)
                    (.object_ (JsonValue/from "trace"))
                    (.otlp (impl/sdk-input-object {:trace-id "otlp_1"}
                                                  SessionTrace$Otlp))
                    (.sessionId "sess_1") (.build))
          page (fn [params]
                 (-> (TraceListPageResponse/builder)
                     (.data [trace]) (.firstId "trace_1") (.hasMore false)
                     (.lastId "trace_1")
                     (.object_ (JsonValue/from "list")) (.build)
                     (as-> response
                       (-> (TraceListPage/builder)
                           (.service @service-ref) (.params params)
                           (.response response) (.build)))))
          service (proxy [TraceService] []
                    (list [params] (swap! calls conj params) (page params)))
          _ (reset! service-ref service)
          sessions (proxy [SessionService] [] (traces [] service))
          agents (proxy [AgentService] [] (sessions [] sessions))
          beta (proxy [BetaService] [] (agents [] agents))
          client (proxy [OpenAIClient] [] (beta [] beta))]
      (is (= [{:id "trace_1" :created-at 1 :object "trace"
               :otlp {:trace-id "otlp_1"} :session-id "sess_1"}]
             (list-traces client "sess_1" {:limit 1 :order :asc})))
      (let [^TraceListParams params (first @calls)]
        (is (= "sess_1" (impl/opt-get (.sessionId params))))
        (is (= 1 (impl/opt-get (.limit params))))
        (is (= "asc" (.asString (impl/opt-get (.order params)))))))
    (is false "session trace listing is not implemented")))
