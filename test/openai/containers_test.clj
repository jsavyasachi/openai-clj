(ns openai.containers-test
  (:require [clojure.test :refer [deftest is testing]]
            [openai.containers :as containers])
  (:import (com.openai.models.containers ContainerCreateParams
                                                ContainerCreateParams$ExpiresAfter
                                                ContainerCreateResponse)
           (com.openai.models.containers.files FileCreateParams FileCreateResponse)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))

(deftest translates-create-params
  (let [^ContainerCreateParams p
        (#'containers/->create-params
         {:name "sandbox" :file-ids ["file-1" "file-2"]
          :expires-after {:anchor :last-active-at :minutes 30}})
        ^ContainerCreateParams$ExpiresAfter expires (opt (.expiresAfter p))]
    (is (= "sandbox" (.name p)))
    (is (= ["file-1" "file-2"] (opt (.fileIds p))))
    (is (= "last_active_at" (.asString (.anchor expires))))
    (is (= 30 (.minutes expires)))))

(deftest converts-container-response-with-present-only-optionals
  (let [base (-> (ContainerCreateResponse/builder)
                 (.id "ctr_1") (.name "sandbox") (.createdAt 10)
                 (.object_ "container") (.status "running"))]
    (is (= {:id "ctr_1" :name "sandbox" :created-at 10 :status :running}
           (#'containers/create-response->map (.build base))))
    (is (= 11 (:last-active-at
               (#'containers/create-response->map (.build (.lastActiveAt base 11))))))))

(deftest translates-file-create-params
  (testing "existing OpenAI file id"
    (let [^FileCreateParams p
          (#'containers/->file-create-params "ctr_1" {:file-id "file_1"})]
      (is (= "ctr_1" (opt (.containerId p))))
      (is (= "file_1" (opt (.fileId p))))))
  (testing "coerced byte input"
    (let [^FileCreateParams p
          (#'containers/->file-create-params "ctr_1" {:file (.getBytes "data" "UTF-8")})]
      (is (= [100 97 116 97]
             (vec (.readAllBytes ^java.io.InputStream (opt (.file p)))))))))

(deftest converts-file-response
  (let [response (-> (FileCreateResponse/builder)
                     (.id "cfile_1") (.bytes 4) (.containerId "ctr_1")
                     (.createdAt 12) (.path "/mnt/data/a.txt") (.source "user")
                     (.build))]
    (is (= {:id "cfile_1" :bytes 4 :container-id "ctr_1" :created-at 12
            :path "/mnt/data/a.txt" :source "user"}
           (#'containers/create-file->map response)))))
