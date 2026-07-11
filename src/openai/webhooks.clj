(ns openai.webhooks
  "Webhook signature verification and event unwrapping."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http Headers)
           (com.openai.models.webhooks WebhookVerificationParams)
           (com.openai.services.blocking WebhookService)))
(set! *warn-on-reflection* true)

(defn- ->headers ^Headers [headers]
  (let [b (Headers/builder)]
    (doseq [[k v] headers]
      (if (sequential? v)
        (.put b (name k) ^java.lang.Iterable v)
        (.put b (name k) (str v))))
    (.build b)))
(defn- ->verification-params ^WebhookVerificationParams [payload headers]
  (let [b (WebhookVerificationParams/builder)]
    (if (bytes? payload) (.payload b ^bytes payload) (.payload b ^String payload))
    (.headers b (->headers headers)) (.build b)))

(defn verify-signature [^OpenAIClient client payload headers]
  (try
    (let [^WebhookService svc (.webhooks client)]
      (.verifySignature svc (->verification-params payload headers)) true)
    (catch Exception e
      (throw (ex-info (or (.getMessage e) "Invalid webhook signature")
                      {:openai/error :webhook-signature} e)))))

(defn unwrap [^OpenAIClient client payload headers]
  (try
    (let [^WebhookService svc (.webhooks client)]
      (impl/sdk-object->clj (.unwrap svc (->verification-params payload headers))))
    (catch Exception e
      (throw (ex-info (or (.getMessage e) "Invalid webhook payload")
                      {:openai/error :webhook-signature} e)))))
