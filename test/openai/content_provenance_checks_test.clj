(ns openai.content-provenance-checks-test
  (:require [clojure.test :refer [deftest is]]
            [openai.content-provenance-checks])
  (:import (com.openai.models.contentprovenancechecks ContentProvenanceCheck
                                                       ContentProvenanceCheck$Object
                                                       ContentProvenanceCheckCreateParams)
           (java.nio.file Files Path)))

(set! *warn-on-reflection* true)

(defn- params ^ContentProvenanceCheckCreateParams [m]
  (#'openai.content-provenance-checks/->create-params m))

(deftest translates-path-file
  (let [^Path path (Files/createTempFile "openai-clj" ".bin" (make-array java.nio.file.attribute.FileAttribute 0))
        ^"[Ljava.nio.file.OpenOption;" opts (make-array java.nio.file.OpenOption 0)
        _ (Files/write path (.getBytes "path-data" "UTF-8") opts)
        p (params {:file path})]
    (is (= "path-data" (String. (.readAllBytes (.file p)) "UTF-8")))))

(deftest translates-byte-file
  (let [p (params {:file (.getBytes "byte-data" "UTF-8")})]
    (is (= "byte-data" (String. (.readAllBytes (.file p)) "UTF-8")))))

(deftest requires-file
  (try
    (params {})
    (is false)
    (catch clojure.lang.ExceptionInfo e
      (is (= {:openai/error :missing-key :key :file} (ex-data e))))))

(deftest converts-response
  (let [^com.openai.models.contentprovenancechecks.ContentProvenanceCheck$Builder b
        (ContentProvenanceCheck/builder)
        ^java.util.List empty-results (java.util.Collections/emptyList)
        _ (.createdAt b 1790000000)
        _ (.object_ b ContentProvenanceCheck$Object/CONTENT_PROVENANCE_CHECK)
        _ (.results b empty-results)
        ^ContentProvenanceCheck response (.build b)]
    (is (= {:created-at 1790000000 :valid? true}
           (#'openai.content-provenance-checks/response->map response)))))
