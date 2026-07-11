(ns openai.containers-test
  (:require [clojure.test :refer [deftest is]] [openai.containers :as containers])
  (:import (com.openai.models.containers ContainerCreateParams
                                                ContainerCreateParams$ExpiresAfter)))
(set! *warn-on-reflection* true)
(defn- opt [o] (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))
(deftest translates-create-params
  (let [^ContainerCreateParams p
        (#'containers/->create-params
         {:name "sandbox" :file-ids ["file-1"]
          :expires-after {:anchor :last-active-at :minutes 30}})]
    (is (= "sandbox" (.name p)))
    (is (= ["file-1"] (opt (.fileIds p))))
    (is (= 30 (.minutes ^ContainerCreateParams$ExpiresAfter (opt (.expiresAfter p)))))))
