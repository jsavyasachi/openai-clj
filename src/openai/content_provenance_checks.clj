(ns openai.content-provenance-checks
  "Idiomatic Clojure wrapper over the OpenAI Content Provenance Checks API."
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.contentprovenancechecks ContentProvenanceCheck
                                                       ContentProvenanceCheckCreateParams
                                                       ContentProvenanceCheckCreateParams$Builder)
           (com.openai.services.blocking ContentProvenanceCheckService)
           (java.io ByteArrayInputStream File InputStream)
           (java.nio.file Files Path)))

(set! *warn-on-reflection* true)

(defn- ->input-stream ^InputStream [input]
  (cond
    (instance? InputStream input) input
    (bytes? input) (ByteArrayInputStream. ^bytes input)
    (instance? Path input) (Files/newInputStream ^Path input (make-array java.nio.file.OpenOption 0))
    (string? input) (Files/newInputStream (.toPath ^File (File. ^String input))
                                         (make-array java.nio.file.OpenOption 0))
    :else (throw (ex-info (str "Unsupported content provenance file type " (class input))
                          {:openai/error :unsupported-file-type :class (class input)}))))

(defn- ->create-params ^ContentProvenanceCheckCreateParams [{:keys [file]}]
  (when-not file (impl/missing-key! :file))
  (let [^ContentProvenanceCheckCreateParams$Builder b
        (ContentProvenanceCheckCreateParams/builder)]
    (cond
      (instance? Path file) (.file b ^Path file)
      (string? file) (.file b (.toPath ^File (File. ^String file)))
      (bytes? file) (.file b ^bytes file)
      :else (.file b ^InputStream (->input-stream file)))
    (.build b)))

(defn- response->map [^ContentProvenanceCheck response]
  {:created-at (.createdAt response)
   :valid? (.isValid response)})

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ContentProvenanceCheckService svc (.contentProvenanceChecks client)]
      (response->map (.create svc (->create-params req))))))
