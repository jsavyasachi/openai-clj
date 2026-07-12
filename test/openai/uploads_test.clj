(ns openai.uploads-test
  (:require [clojure.test :refer [deftest is]]
            [openai.uploads :as uploads])
  (:import (com.openai.core JsonValue)
           (com.openai.models.uploads Upload Upload$Status UploadCompleteParams UploadCreateParams)
           (com.openai.models.uploads.parts PartCreateParams)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o) (.get ^java.util.Optional o)))

(deftest translates-create-params
  (let [^UploadCreateParams p (#'uploads/->create-params
                               {:filename "data.jsonl" :bytes 12
                                :mime-type "application/jsonl" :purpose :fine-tune})]
    (is (= "data.jsonl" (.filename p)))
    (is (= 12 (.bytes p)))
    (is (= "application/jsonl" (.mimeType p)))
    (is (= "fine_tune" (.asString (.purpose p))))))

(deftest converts-upload-response
  (let [upload (-> (Upload/builder)
                   (.id "upload_1") (.bytes 12) (.createdAt 20) (.expiresAt 30)
                   (.filename "data.jsonl") (.object_ (JsonValue/from "upload"))
                   (.purpose "fine-tune") (.status (Upload$Status/of "pending"))
                   (.build))]
    (is (= {:id "upload_1" :filename "data.jsonl" :bytes 12
            :purpose :fine-tune :status :pending :created-at 20 :expires-at 30}
           (#'uploads/upload->map upload)))))

(deftest coerces-add-part-bytes
  (let [^PartCreateParams p (#'uploads/->part-params
                             "upload_1" (.getBytes "part" "UTF-8"))]
    (is (= "upload_1" (opt (.uploadId p))))
    (is (= [112 97 114 116] (vec (.readAllBytes (.data p)))))))

(deftest translates-complete-params
  (let [^UploadCompleteParams p
        (#'uploads/->complete-params "upload_1" {:part-ids ["part_1" "part_2"]})]
    (is (= "upload_1" (opt (.uploadId p))))
    (is (= ["part_1" "part_2"] (.partIds p)))))
