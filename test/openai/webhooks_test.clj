(ns openai.webhooks-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.webhooks :as webhooks])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http Headers)
           (com.openai.models.webhooks WebhookVerificationParams)
           (com.openai.services.blocking WebhookService)
           (java.lang.reflect InvocationHandler Proxy)))

(set! *warn-on-reflection* true)

(defn- throwing-client ^OpenAIClient []
  (let [handler (reify InvocationHandler
                  (invoke [_ _ _ _]
                    (throw (IllegalArgumentException. "bad signature"))))
        service (Proxy/newProxyInstance (.getClassLoader WebhookService)
                                        (into-array Class [WebhookService]) handler)
        client-handler (reify InvocationHandler
                         (invoke [_ _ method _]
                           (if (= "webhooks" (.getName ^java.lang.reflect.Method method))
                             service
                             (throw (UnsupportedOperationException.)))))]
    (cast OpenAIClient
          (Proxy/newProxyInstance (.getClassLoader OpenAIClient)
                                  (into-array Class [OpenAIClient]) client-handler))))

(deftest translates-headers
  (let [^Headers headers (#'webhooks/->headers
                          {:webhook-id "msg_1" :webhook-signature ["v1,a" "v1,b"]})]
    (is (= ["msg_1"] (.values headers "webhook-id")))
    (is (= ["v1,a" "v1,b"] (.values headers "webhook-signature")))))

(deftest translates-verification-payloads
  (testing "String payload"
    (let [^WebhookVerificationParams p
          (#'webhooks/->verification-params "payload" {"webhook-id" "msg_1"})]
      (is (= "payload" (String. (.payload p) "UTF-8")))
      (is (= ["msg_1"] (.values (.headers p) "webhook-id")))))
  (testing "byte array payload"
    (let [^WebhookVerificationParams p
          (#'webhooks/->verification-params (.getBytes "bytes" "UTF-8") {})]
      (is (= [98 121 116 101 115] (vec (.payload p)))))))

(deftest wraps-webhook-errors
  (let [client (throwing-client)]
    (doseq [call [#(webhooks/verify-signature client "payload" {})
                  #(webhooks/unwrap client "payload" {})]]
      (let [error (try (call) nil (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:openai/error :webhook-signature} (ex-data error)))
        (is (= "bad signature" (.getMessage ^Exception error)))
        (is (instance? IllegalArgumentException (.getCause ^Exception error)))))))
