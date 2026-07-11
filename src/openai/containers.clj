(ns openai.containers
  "Idiomatic Clojure wrapper over the OpenAI Containers API."
  (:refer-clojure :exclude [list])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http HttpResponse)
           (com.openai.models.containers ContainerCreateParams
                                           ContainerCreateParams$ExpiresAfter
                                           ContainerCreateParams$ExpiresAfter$Anchor
                                           ContainerCreateResponse ContainerListPage
                                           ContainerListParams ContainerListResponse
                                           ContainerRetrieveResponse)
           (com.openai.models.containers.files FileCreateParams FileCreateResponse
                                                 FileDeleteParams FileListPage FileListParams
                                                 FileListResponse FileRetrieveParams
                                                 FileRetrieveResponse)
           (com.openai.models.containers.files.content ContentRetrieveParams)
           (com.openai.services.blocking ContainerService)
           (com.openai.services.blocking.containers FileService)
           (com.openai.services.blocking.containers.files ContentService)
           (java.io File InputStream)
           (java.nio.file Path)))

(set! *warn-on-reflection* true)

(defn- ->expires-after ^ContainerCreateParams$ExpiresAfter [{:keys [anchor minutes]}]
  (when-not minutes (impl/missing-key! :minutes))
  (-> (ContainerCreateParams$ExpiresAfter/builder)
      (.anchor (ContainerCreateParams$ExpiresAfter$Anchor/of
                (impl/enum-name (or anchor :last-active-at))))
      (.minutes (long minutes)) (.build)))

(defn- ->create-params ^ContainerCreateParams [{:keys [name file-ids expires-after]}]
  (when-not name (impl/missing-key! :name))
  (let [b (ContainerCreateParams/builder)]
    (.name b ^String name)
    (when file-ids (.fileIds b ^java.util.List file-ids))
    (when expires-after (.expiresAfter b (->expires-after expires-after)))
    (.build b)))

(defn- create-response->map [^ContainerCreateResponse c]
  (cond-> {:id (.id c) :name (.name c) :created-at (.createdAt c)
           :status (impl/->keyword (.status c))}
    (.isPresent (.lastActiveAt c)) (assoc :last-active-at (impl/opt-get (.lastActiveAt c)))))

(defn- retrieve-response->map [^ContainerRetrieveResponse c]
  (cond-> {:id (.id c) :name (.name c) :created-at (.createdAt c)
           :status (impl/->keyword (.status c))}
    (.isPresent (.lastActiveAt c)) (assoc :last-active-at (impl/opt-get (.lastActiveAt c)))))

(defn- list-response->map [^ContainerListResponse c]
  (cond-> {:id (.id c) :name (.name c) :created-at (.createdAt c)
           :status (impl/->keyword (.status c))}
    (.isPresent (.lastActiveAt c)) (assoc :last-active-at (impl/opt-get (.lastActiveAt c)))))

(defn create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^ContainerService svc (.containers client)]
      (create-response->map (.create svc (->create-params req))))))

(defn retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^ContainerService svc (.containers client)]
      (retrieve-response->map (.retrieve svc id)))))

(defn- ->list-params ^ContainerListParams [{:keys [after limit order]}]
  (let [b (ContainerListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.containers.ContainerListParams$Order/of (name order))))
    (.build b)))

(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^ContainerService svc (.containers client)
           ^ContainerListPage page (.list svc (->list-params opts))]
       (mapv list-response->map (impl/all-pages page))))))

(defn delete [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^ContainerService svc (.containers client)] (.delete svc id) nil)))

(defn- ->file-create-params ^FileCreateParams [^String container-id {:keys [file file-id]}]
  (when-not (or file file-id) (impl/missing-key! :file))
  (let [b (FileCreateParams/builder)]
    (.containerId b container-id)
    (when file-id (.fileId b ^String file-id))
    (when file
      (cond (bytes? file) (.file b ^bytes file)
            (instance? InputStream file) (.file b ^InputStream file)
            (instance? Path file) (.file b ^Path file)
            (string? file) (.file b (.toPath (File. ^String file)))
            :else (throw (ex-info "Unsupported :file type"
                                  {:openai/error :unsupported-file-type :class (class file)}))))
    (.build b)))

(defn- create-file->map [^FileCreateResponse f]
  {:id (.id f) :bytes (.bytes f) :container-id (.containerId f)
   :created-at (.createdAt f) :path (.path f) :source (.source f)})
(defn- retrieve-file->map [^FileRetrieveResponse f]
  {:id (.id f) :bytes (.bytes f) :container-id (.containerId f)
   :created-at (.createdAt f) :path (.path f) :source (.source f)})
(defn- list-file->map [^FileListResponse f]
  {:id (.id f) :bytes (.bytes f) :container-id (.containerId f)
   :created-at (.createdAt f) :path (.path f) :source (.source f)})

(defn create-file [^OpenAIClient client ^String container-id req]
  (impl/with-api-errors
    (let [^FileService svc (.files (.containers client))]
      (create-file->map (.create svc (->file-create-params container-id req))))))

(defn- ->file-retrieve-params ^FileRetrieveParams [^String container-id ^String file-id]
  (-> (FileRetrieveParams/builder) (.containerId container-id) (.fileId file-id) (.build)))

(defn retrieve-file [^OpenAIClient client ^String container-id ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files (.containers client))]
      (retrieve-file->map (.retrieve svc (->file-retrieve-params container-id file-id))))))

(defn- ->file-list-params ^FileListParams [^String container-id {:keys [after limit order]}]
  (let [b (FileListParams/builder)]
    (.containerId b container-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.containers.files.FileListParams$Order/of (name order))))
    (.build b)))

(defn list-files
  ([^OpenAIClient client ^String container-id] (list-files client container-id {}))
  ([^OpenAIClient client ^String container-id opts]
   (impl/with-api-errors
     (let [^FileService svc (.files (.containers client))
           ^FileListPage page (.list svc (->file-list-params container-id opts))]
       (mapv list-file->map (impl/all-pages page))))))

(defn delete-file [^OpenAIClient client ^String container-id ^String file-id]
  (impl/with-api-errors
    (let [^FileService svc (.files (.containers client))
          p (-> (FileDeleteParams/builder) (.containerId container-id) (.fileId file-id) (.build))]
      (.delete svc p) nil)))

(defn file-content ^bytes [^OpenAIClient client ^String container-id ^String file-id]
  (impl/with-api-errors
    (let [^ContentService svc (.content (.files (.containers client)))
          p (-> (ContentRetrieveParams/builder) (.containerId container-id) (.fileId file-id) (.build))]
      (with-open [^HttpResponse response (.retrieve svc p)]
        (.readAllBytes (.body response))))))
