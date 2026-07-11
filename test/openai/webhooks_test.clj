(ns openai.webhooks-test
  (:require [clojure.test :refer [deftest is]] [openai.webhooks :as webhooks])
  (:import (com.openai.models.webhooks WebhookVerificationParams)))
(set! *warn-on-reflection* true)
(deftest translates-verification-params
  (let [^WebhookVerificationParams p
        (#'webhooks/->verification-params "{\"type\":\"response.completed\"}"
         {"webhook-id" "msg_1" "webhook-timestamp" "123" "webhook-signature" "v1,test"})]
    (is (= "{\"type\":\"response.completed\"}" (String. (.payload p) "UTF-8")))
    (is (= ["msg_1"] (.values (.headers p) "webhook-id")))))
