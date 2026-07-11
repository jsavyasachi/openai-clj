(ns openai.vector-stores-test
  (:require [clojure.test :refer [deftest is]]
            [openai.vector-stores :as vector-stores])
  (:import (com.openai.models.vectorstores VectorStore
                                                VectorStore$FileCounts
                                                VectorStore$Status
                                                VectorStoreCreateParams
                                                VectorStoreCreateParams$ExpiresAfter)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(deftest translates-create-params
  (let [^VectorStoreCreateParams p
        (#'vector-stores/->create-params
         {:name "docs" :file-ids ["file-1"]
          :expires-after {:anchor :last-active-at :days 7}
          :metadata {:team "search"}})]
    (is (= "docs" (opt (.name p))))
    (is (= ["file-1"] (opt (.fileIds p))))
    (is (= 7 (.days ^VectorStoreCreateParams$ExpiresAfter
                    (opt (.expiresAfter p)))))))

(deftest converts-vector-store
  (let [counts (-> (VectorStore$FileCounts/builder)
                   (.cancelled 1) (.completed 2) (.failed 3)
                   (.inProgress 4) (.total 10) (.build))
        store (let [b (VectorStore/builder)]
                (.id b "vs-1") (.name b "docs") (.createdAt b 100)
                (.lastActiveAt b (java.util.Optional/empty))
                (.metadata b (java.util.Optional/empty))
                (.expiresAfter b (com.openai.core.JsonField/ofNullable nil))
                (.expiresAt b (java.util.Optional/empty))
                (.status b VectorStore$Status/COMPLETED) (.usageBytes b 42)
                (.fileCounts b counts) (.build b))]
    (is (= {:id "vs-1" :name "docs" :created-at 100
            :status :completed :usage-bytes 42
            :file-counts {:cancelled 1 :completed 2 :failed 3
                          :in-progress 4 :total 10}}
           (#'vector-stores/vector-store->map store)))))

(deftest exposes-file-batch-prefix-api
  (doseq [sym '[file-batch-create file-batch-retrieve file-batch-cancel]]
    (is (fn? (some-> (ns-resolve 'openai.vector-stores sym) deref)))))
