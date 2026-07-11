(ns openai.completions-test
  (:require [clojure.test :refer [deftest is]]
            [openai.completions]
            [openai.impl :as impl])
  (:import (com.openai.models.completions Completion CompletionChoice
                                             CompletionChoice$Builder
                                             CompletionChoice$FinishReason
                                             CompletionCreateParams
                                             CompletionCreateParams$Model
                                             CompletionCreateParams$Prompt
                                             CompletionCreateParams$Stop
                                             CompletionUsage)))

(set! *warn-on-reflection* true)

(deftest translates-params
  (let [^CompletionCreateParams params (#'openai.completions/->create-params
                {:model "gpt-3.5-turbo-instruct" :prompt ["a" "b"]
                 :stop ["x" "y"] :max-tokens 12 :temperature 0.2
                 :top-p 0.8 :n 2 :echo true})
        ^CompletionCreateParams$Model model (.model params)
        ^CompletionCreateParams$Prompt prompt (impl/opt-get (.prompt params))
        ^CompletionCreateParams$Stop stop (impl/opt-get (.stop params))]
    (is (= "gpt-3.5-turbo-instruct" (.asString model)))
    (is (= ["a" "b"] (.asArrayOfStrings prompt)))
    (is (= ["x" "y"] (.asStrings stop)))
    (is (= 12 (impl/opt-get (.maxTokens params))))
    (is (= 0.2 (impl/opt-get (.temperature params))))
    (is (= true (impl/opt-get (.echo params))))))

(deftest converts-completion
  (let [^CompletionChoice$Builder cb (CompletionChoice/builder)
        choice (do (.index cb 0) (.text cb "hello")
                   (.logprobs cb (java.util.Optional/empty))
                   (.finishReason cb CompletionChoice$FinishReason/STOP) (.build cb))
        usage (-> (CompletionUsage/builder) (.promptTokens 2)
                  (.completionTokens 1) (.totalTokens 3) (.build))
        completion (-> (Completion/builder) (.id "cmpl-1") (.model "legacy")
                       (.created 42) (.addChoice choice) (.usage usage) (.build))
        m (#'openai.completions/completion->map completion)]
    (is (= {:id "cmpl-1" :model "legacy" :created 42
            :choices [{:index 0 :text "hello" :finish-reason :stop}]
            :usage {:prompt-tokens 2 :completion-tokens 1 :total-tokens 3}
            :text "hello"}
           m))))
