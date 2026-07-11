(ns openai.skills
  "Idiomatic Clojure wrapper over the OpenAI Skills API."
  (:refer-clojure :exclude [list update])
  (:require [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core.http HttpResponse)
           (com.openai.models.skills DeletedSkill Skill SkillCreateParams
                                      SkillCreateParams$Builder SkillListPage SkillListParams)
           (com.openai.models.skills.versions DeletedSkillVersion SkillVersion
                                                VersionCreateParams VersionCreateParams$Builder
                                                VersionListPage VersionListParams)
           (com.openai.services.blocking SkillService)
           (com.openai.services.blocking.skills VersionService)))
(set! *warn-on-reflection* true)

(defn- set-skill-files! [^SkillCreateParams$Builder builder files]
  (cond (bytes? files) (.files builder ^bytes files)
        (instance? java.io.InputStream files) (.files builder ^java.io.InputStream files)
        (instance? java.nio.file.Path files) (.files builder ^java.nio.file.Path files)
        (string? files) (.files builder (.toPath (java.io.File. ^String files)))
        (vector? files) (.filesOfInputStreams builder ^java.util.List files)
        :else (throw (ex-info "Unsupported skill files" {:openai/error :unsupported-file-type}))))
(defn- set-version-files! [^VersionCreateParams$Builder builder files]
  (cond (bytes? files) (.files builder ^bytes files)
        (instance? java.io.InputStream files) (.files builder ^java.io.InputStream files)
        (instance? java.nio.file.Path files) (.files builder ^java.nio.file.Path files)
        (string? files) (.files builder (.toPath (java.io.File. ^String files)))
        (vector? files) (.filesOfInputStreams builder ^java.util.List files)
        :else (throw (ex-info "Unsupported skill files" {:openai/error :unsupported-file-type}))))

(defn- ->create-params ^SkillCreateParams [{:keys [files]}]
  (when-not files (impl/missing-key! :files))
  (let [b (SkillCreateParams/builder)] (set-skill-files! b files) (.build b)))
(defn- skill->map [^Skill s]
  {:id (.id s) :name (.name s) :description (.description s)
   :created-at (.createdAt s) :default-version (.defaultVersion s)
   :latest-version (.latestVersion s)})
(defn create [^OpenAIClient client req]
  (impl/with-api-errors (let [^SkillService svc (.skills client)]
                          (skill->map (.create svc (->create-params req))))))
(defn retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^SkillService svc (.skills client)] (skill->map (.retrieve svc id)))))
(defn update [^OpenAIClient client ^String id req]
  (impl/with-api-errors
    (let [b (com.openai.models.skills.SkillUpdateParams/builder) _ (.skillId b id)
          _ (doseq [[k v] req] (.putAdditionalBodyProperty b (impl/enum-name k)
                                                          (com.openai.core.JsonValue/from v)))
          ^SkillService svc (.skills client)] (skill->map (.update svc (.build b))))))
(defn list
  ([^OpenAIClient client] (list client {}))
  ([^OpenAIClient client {:keys [after limit order]}]
   (impl/with-api-errors
     (let [b (SkillListParams/builder) _ (when after (.after b ^String after))
           _ (when limit (.limit b (long limit)))
           _ (when order (.order b (com.openai.models.skills.SkillListParams$Order/of (name order))))
           ^SkillService svc (.skills client) ^SkillListPage page (.list svc (.build b))]
       (mapv skill->map (impl/all-pages page))))))
(defn delete [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [^SkillService svc (.skills client) ^DeletedSkill d (.delete svc id)]
      {:id (.id d) :deleted (.deleted d)})))
(defn content ^bytes [^OpenAIClient client ^String id]
  (impl/with-api-errors
    (let [svc (.content (.skills client))]
      (with-open [^HttpResponse r (.retrieve ^com.openai.services.blocking.skills.ContentService svc id)]
        (.readAllBytes (.body r))))))

(defn- ->version-create-params ^VersionCreateParams [^String skill-id {:keys [files default]}]
  (when-not files (impl/missing-key! :files))
  (let [b (VersionCreateParams/builder)]
    (.skillId b skill-id) (set-version-files! b files)
    (when (some? default) (.default_ b (boolean default))) (.build b)))
(defn- version->map [^SkillVersion v]
  {:id (.id v) :skill-id (.skillId v) :version (.version v) :name (.name v)
   :description (.description v) :created-at (.createdAt v)})
(defn create-version [^OpenAIClient client ^String skill-id req]
  (impl/with-api-errors (let [^VersionService svc (.versions (.skills client))]
                          (version->map (.create svc (->version-create-params skill-id req))))))
(defn retrieve-version [^OpenAIClient client ^String skill-id ^String version]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.skills.versions.VersionRetrieveParams/builder)
                (.skillId skill-id) (.version version) (.build))
          ^VersionService svc (.versions (.skills client))] (version->map (.retrieve svc p)))))
(defn list-versions
  ([^OpenAIClient client ^String skill-id] (list-versions client skill-id {}))
  ([^OpenAIClient client ^String skill-id {:keys [after limit order]}]
   (impl/with-api-errors
     (let [b (VersionListParams/builder) _ (.skillId b skill-id)
           _ (when after (.after b ^String after)) _ (when limit (.limit b (long limit)))
           _ (when order (.order b (com.openai.models.skills.versions.VersionListParams$Order/of (name order))))
           ^VersionService svc (.versions (.skills client)) ^VersionListPage page (.list svc (.build b))]
       (mapv version->map (impl/all-pages page))))))
(defn delete-version [^OpenAIClient client ^String skill-id ^String version]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.skills.versions.VersionDeleteParams/builder)
                (.skillId skill-id) (.version version) (.build))
          ^VersionService svc (.versions (.skills client)) ^DeletedSkillVersion d (.delete svc p)]
      {:id (.id d) :deleted (.deleted d)})))
(defn version-content ^bytes [^OpenAIClient client ^String skill-id ^String version]
  (impl/with-api-errors
    (let [p (-> (com.openai.models.skills.versions.content.ContentRetrieveParams/builder)
                (.skillId skill-id) (.version version) (.build))
          svc (.content (.versions (.skills client)))]
      (with-open [^HttpResponse r (.retrieve ^com.openai.services.blocking.skills.versions.ContentService svc p)]
        (.readAllBytes (.body r))))))

(def version-create create-version)
(def version-retrieve retrieve-version)
(def version-list list-versions)
(def version-delete delete-version)
