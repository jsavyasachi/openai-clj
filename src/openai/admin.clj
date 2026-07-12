(ns openai.admin
  "Organization-level OpenAI Admin API wrappers."
  (:require [clojure.string :as str] [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue Page)
           (com.openai.models.admin.organization.adminapikeys AdminApiKey AdminApiKey$Owner AdminApiKeyCreateParams AdminApiKeyCreateResponse AdminApiKeyDeleteResponse AdminApiKeyListPage AdminApiKeyListParams AdminApiKeyListParams$Order)
           (com.openai.models.admin.organization.auditlogs AuditLogListPage AuditLogListParams AuditLogListParams$EffectiveAt AuditLogListParams$EventType AuditLogListResponse)
           (com.openai.models.admin.organization.certificates Certificate Certificate$CertificateDetails CertificateActivatePage CertificateActivateParams CertificateActivateResponse CertificateActivateResponse$CertificateDetails CertificateCreateParams CertificateDeactivatePage CertificateDeactivateParams CertificateDeactivateResponse CertificateDeactivateResponse$CertificateDetails CertificateDeleteResponse CertificateListPage CertificateListParams CertificateListParams$Order CertificateListResponse CertificateListResponse$CertificateDetails CertificateRetrieveParams CertificateRetrieveParams$Include CertificateUpdateParams)
           (com.openai.models.admin.organization.dataretention DataRetentionUpdateParams DataRetentionUpdateParams$RetentionType OrganizationDataRetention)
           (com.openai.models.admin.organization.invites Invite Invite$Project InviteCreateParams InviteCreateParams$Project InviteCreateParams$Project$Role InviteCreateParams$Role InviteDeleteResponse InviteListPage InviteListParams)
           (com.openai.models.admin.organization.projects ProjectCreateParams)
           (com.openai.services.blocking.admin OrganizationService)
           (com.openai.services.blocking.admin.organization AdminApiKeyService AuditLogService CertificateService DataRetentionService InviteService)
           (java.lang.reflect Method Modifier)))
(set! *warn-on-reflection* true)

(defn- ->project-create-params ^ProjectCreateParams [{:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (ProjectCreateParams/builder) (.name ^String name) (.build)))

(defn- camel [k]
  (let [[head & tail] (str/split (name k) #"-")]
    (apply str head (map str/capitalize tail))))

(declare build-model)

(defn- static-method ^Method [^Class cls ^String name arity]
  (first (filter #(and (= name (.getName ^Method %))
                       (= arity (.getParameterCount ^Method %))
                       (Modifier/isStatic (.getModifiers ^Method %)))
                 (.getMethods cls))))

(defn- coerce-value [^Class target value]
  (cond
    (nil? value) nil
    (.isInstance target value) value
    (= target JsonValue) (JsonValue/from value)
    (and (.isPrimitive target) (= target Boolean/TYPE)) (boolean value)
    (and (.isPrimitive target) (= target Long/TYPE)) (long value)
    (and (.isPrimitive target) (= target Integer/TYPE)) (int value)
    (and (.isPrimitive target) (= target Double/TYPE)) (double value)
    (and (map? value) (static-method target "builder" 0)) (build-model target value)
    :else (if-let [of (static-method target "of" 1)]
            (.invoke of nil (object-array [(impl/enum-name value)]))
            value)))

(defn- invoke-setter! [builder k value]
  (let [method-name (camel k)
        methods (filter #(and (= method-name (.getName ^Method %))
                              (= 1 (.getParameterCount ^Method %)))
                        (.getMethods (class builder)))]
    (or
     (some (fn [^Method m]
             (try
               (.invoke m builder
                        (object-array [(coerce-value (aget (.getParameterTypes m) 0) value)]))
               true
               (catch Exception _ false)))
           methods)
     (when-let [^Method m (first (filter #(and (= "putAdditionalBodyProperty" (.getName ^Method %))
                                               (= 2 (.getParameterCount ^Method %)))
                                         (.getMethods (class builder))))]
       (.invoke m builder (object-array [(impl/enum-name k) (JsonValue/from value)]))
       true)
     (throw (ex-info (str "Unsupported admin parameter " k)
                     {:openai/error :unsupported-parameter :key k
                      :builder (class builder)})))))

(defn- build-model [^Class cls values]
  (let [^Method factory (static-method cls "builder" 0)
        builder (.invoke factory nil (object-array 0))]
    (doseq [[k v] values] (invoke-setter! builder k v))
    (let [^Method build (first (filter #(and (= "build" (.getName ^Method %))
                                             (zero? (.getParameterCount ^Method %)))
                                       (.getMethods (class builder))))]
      (.invoke build builder (object-array 0)))))

(defn- service-at [^OpenAIClient client path]
  (reduce (fn [target accessor]
            (let [^Method method (first (filter #(and (= (name accessor) (.getName ^Method %))
                                                       (zero? (.getParameterCount ^Method %)))
                                                 (.getMethods (class target))))]
              (.invoke method target (object-array 0))))
          client (into [:admin :organization] path)))

(defn- operation-method ^Method [service action]
  (or (first (filter #(and (= (name action) (.getName ^Method %))
                           (= 1 (.getParameterCount ^Method %))
                           (str/ends-with? (.getName ^Class (aget (.getParameterTypes ^Method %) 0)) "Params"))
                     (.getMethods (class service))))
      (first (filter #(and (= (name action) (.getName ^Method %))
                           (zero? (.getParameterCount ^Method %)))
                     (.getMethods (class service))))))

(defn request
  "Invoke an Admin API operation. `path` is the service accessor path after
  `admin().organization()`, `action` is the SDK operation, and `params` is a
  kebab-case map including path/query/body identifiers. Lists auto-page."
  [^OpenAIClient client path action params]
  (impl/with-api-errors
    (let [service (service-at client path)
          ^Method method (operation-method service action)
          param-types (.getParameterTypes method)
          result (if (zero? (alength param-types))
                   (.invoke method service (object-array 0))
                   (.invoke method service
                            (object-array [(build-model (aget param-types 0) params)])))]
      (cond
        (instance? Page result) (mapv impl/sdk-object->clj (impl/all-pages result))
        (nil? result) nil
        :else (impl/sdk-object->clj result)))))

(defn- operation-name [prefix action]
  (symbol (str prefix "-" (-> action name
                               (str/replace #"([a-z])([A-Z])" "$1-$2")
                               str/lower-case))))
(defmacro ^:private defadmin [prefix path actions]
  `(do ~@(for [action actions]
           `(defn ~(operation-name prefix action) [client# params#]
              (request client# ~path ~action params#)))))

(defn- organization ^OrganizationService [^OpenAIClient client]
  (.organization (.admin client)))

(defn- ->admin-api-key-create-params ^AdminApiKeyCreateParams [{:keys [name expires-in-seconds]}]
  (when-not name (impl/missing-key! :name))
  (let [b (AdminApiKeyCreateParams/builder)]
    (.name b ^String name)
    (when expires-in-seconds (.expiresInSeconds b (long expires-in-seconds)))
    (.build b)))

(defn- ->admin-api-key-list-params ^AdminApiKeyListParams [{:keys [after limit order]}]
  (let [b (AdminApiKeyListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (AdminApiKeyListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- admin-api-key-owner->map [^AdminApiKey$Owner o]
  (cond-> {}
    (.isPresent (.id o)) (assoc :id (impl/opt-get (.id o)))
    (.isPresent (.createdAt o)) (assoc :created-at (impl/opt-get (.createdAt o)))
    (.isPresent (.name o)) (assoc :name (impl/opt-get (.name o)))
    (.isPresent (.role o)) (assoc :role (impl/opt-get (.role o)))
    (.isPresent (.type o)) (assoc :type (impl/opt-get (.type o)))))
(defn- admin-api-key->map [^AdminApiKey k]
  (cond-> {:id (.id k) :created-at (.createdAt k) :owner (admin-api-key-owner->map (.owner k))
           :redacted-value (.redactedValue k)}
    (.isPresent (.expiresAt k)) (assoc :expires-at (impl/opt-get (.expiresAt k)))
    (.isPresent (.lastUsedAt k)) (assoc :last-used-at (impl/opt-get (.lastUsedAt k)))
    (.isPresent (.name k)) (assoc :name (impl/opt-get (.name k)))))

(defn- admin-api-key-create-response->map [^AdminApiKeyCreateResponse k]
  (assoc (admin-api-key->map (.toAdminApiKey k)) :value (.value k)))

(defn admin-api-key-create [^OpenAIClient client req]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))]
      (admin-api-key-create-response->map (.create svc (->admin-api-key-create-params req))))))
(defn admin-api-key-retrieve [^OpenAIClient client ^String key-id]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))]
      (admin-api-key->map (.retrieve svc key-id)))))
(defn admin-api-key-list
  ([^OpenAIClient client] (admin-api-key-list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^AdminApiKeyService svc (.adminApiKeys (organization client))
           ^AdminApiKeyListPage page (.list svc (->admin-api-key-list-params opts))]
       (mapv admin-api-key->map (impl/all-pages page))))))
(defn admin-api-key-delete [^OpenAIClient client ^String key-id]
  (impl/with-api-errors
    (let [^AdminApiKeyService svc (.adminApiKeys (organization client))
          ^AdminApiKeyDeleteResponse r (.delete svc key-id)]
      {:id (.id r) :deleted (.deleted r)})))

(defn- ->effective-at ^AuditLogListParams$EffectiveAt [{:keys [gt gte lt lte]}]
  (let [b (AuditLogListParams$EffectiveAt/builder)]
    (when gt (.gt b (long gt))) (when gte (.gte b (long gte)))
    (when lt (.lt b (long lt))) (when lte (.lte b (long lte))) (.build b)))
(defn- ->audit-log-list-params ^AuditLogListParams [{:keys [effective-at project-ids event-types actor-ids actor-emails resource-ids limit after before]}]
  (let [b (AuditLogListParams/builder)]
    (when effective-at (.effectiveAt b (->effective-at effective-at)))
    (when project-ids (.projectIds b ^java.util.List project-ids))
    (when event-types (.eventTypes b ^java.util.List (mapv #(AuditLogListParams$EventType/of (impl/enum-name %)) event-types)))
    (when actor-ids (.actorIds b ^java.util.List actor-ids))
    (when actor-emails (.actorEmails b ^java.util.List actor-emails))
    (when resource-ids (.resourceIds b ^java.util.List resource-ids))
    (when limit (.limit b (long limit))) (when after (.after b ^String after))
    (when before (.before b ^String before)) (.build b)))
(defn- audit-log->map [^AuditLogListResponse a]
  {:id (.id a) :effective-at (.effectiveAt a) :type (impl/->keyword (.type a))})
(defn audit-log-list
  ([^OpenAIClient client] (audit-log-list client {}))
  ([^OpenAIClient client opts]
   (impl/with-api-errors
     (let [^AuditLogService svc (.auditLogs (organization client))
           ^AuditLogListPage page (.list svc (->audit-log-list-params opts))]
       (mapv audit-log->map (impl/all-pages page))))))

(defn- ->certificate-create-params ^CertificateCreateParams [{:keys [certificate name]}]
  (when-not certificate (impl/missing-key! :certificate))
  (let [b (CertificateCreateParams/builder)] (.certificate b ^String certificate)
        (when name (.name b ^String name)) (.build b)))
(defn- ->certificate-retrieve-params ^CertificateRetrieveParams [^String id {:keys [include]}]
  (let [b (CertificateRetrieveParams/builder)] (.certificateId b id)
    (when include (.include b ^java.util.List (mapv #(CertificateRetrieveParams$Include/of (impl/enum-name %)) include))) (.build b)))
(defn- ->certificate-update-params ^CertificateUpdateParams [^String id {:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (CertificateUpdateParams/builder) (.certificateId id) (.name ^String name) (.build)))
(defn- ->certificate-list-params ^CertificateListParams [{:keys [after limit order]}]
  (let [b (CertificateListParams/builder)] (when after (.after b ^String after))
    (when limit (.limit b (long limit))) (when order (.order b (CertificateListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- certificate-details->map [^Certificate$CertificateDetails d]
  (cond-> {} (.isPresent (.content d)) (assoc :content (impl/opt-get (.content d)))
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-list-details->map [^CertificateListResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-activate-details->map [^CertificateActivateResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate-deactivate-details->map [^CertificateDeactivateResponse$CertificateDetails d]
  (cond-> {} (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))
(defn- certificate->map [^Certificate c]
  (cond-> {:id (.id c) :certificate-details (certificate-details->map (.certificateDetails c)) :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))
    (.isPresent (.active c)) (assoc :active (impl/opt-get (.active c)))))
(defn- certificate-list->map [^CertificateListResponse c]
  (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-list-details->map (.certificateDetails c)) :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn certificate-create [^OpenAIClient client req] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.create s (->certificate-create-params req))))))
(defn certificate-retrieve
  ([^OpenAIClient client ^String id] (certificate-retrieve client id {}))
  ([^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.retrieve s (->certificate-retrieve-params id opts)))))))
(defn certificate-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client))] (certificate->map (.update s (->certificate-update-params id opts))))))
(defn certificate-list
  ([^OpenAIClient client] (certificate-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateListPage p (.list s (->certificate-list-params opts))] (mapv certificate-list->map (impl/all-pages p))))))
(defn certificate-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateDeleteResponse r (.delete s id)] {:id (.id r)})))
(defn- ->certificate-activate-params ^CertificateActivateParams [^String id]
  (let [b (CertificateActivateParams/builder)
        ids (doto (java.util.ArrayList.) (.add id))]
    (.certificateIds b ids) (.build b)))
(defn- ->certificate-deactivate-params ^CertificateDeactivateParams [^String id]
  (let [b (CertificateDeactivateParams/builder)
        ids (doto (java.util.ArrayList.) (.add id))]
    (.certificateIds b ids) (.build b)))
(defn- certificate-activate->map [^CertificateActivateResponse c] (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-activate-details->map (.certificateDetails c)) :created-at (.createdAt c)} (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn- certificate-deactivate->map [^CertificateDeactivateResponse c] (cond-> {:id (.id c) :active (.active c) :certificate-details (certificate-deactivate-details->map (.certificateDetails c)) :created-at (.createdAt c)} (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))
(defn certificate-activate [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateActivatePage p (.activate s (->certificate-activate-params id))] (mapv certificate-activate->map (impl/all-pages p)))))
(defn certificate-deactivate [^OpenAIClient client ^String id] (impl/with-api-errors (let [^CertificateService s (.certificates (organization client)) ^CertificateDeactivatePage p (.deactivate s (->certificate-deactivate-params id))] (mapv certificate-deactivate->map (impl/all-pages p)))))

(defn- data-retention->map [^OrganizationDataRetention d] {:type (impl/->keyword (.type d))})
(defn- ->data-retention-update-params ^DataRetentionUpdateParams [{:keys [retention-type]}]
  (when-not retention-type (impl/missing-key! :retention-type))
  (-> (DataRetentionUpdateParams/builder) (.retentionType (DataRetentionUpdateParams$RetentionType/of (impl/enum-name retention-type))) (.build)))
(defn data-retention-retrieve [^OpenAIClient client] (impl/with-api-errors (let [^DataRetentionService s (.dataRetention (organization client))] (data-retention->map (.retrieve s)))))
(defn data-retention-update [^OpenAIClient client req] (impl/with-api-errors (let [^DataRetentionService s (.dataRetention (organization client))] (data-retention->map (.update s (->data-retention-update-params req))))))
(defadmin "group" [:groups] [:create :retrieve :update :list :delete])
(defn- ->invite-project ^InviteCreateParams$Project [{:keys [id role]}]
  (when-not id (impl/missing-key! :id)) (when-not role (impl/missing-key! :role))
  (-> (InviteCreateParams$Project/builder) (.id ^String id) (.role (InviteCreateParams$Project$Role/of (impl/enum-name role))) (.build)))
(defn- ->invite-create-params ^InviteCreateParams [{:keys [email role projects]}]
  (when-not email (impl/missing-key! :email)) (when-not role (impl/missing-key! :role))
  (let [b (InviteCreateParams/builder)] (.email b ^String email) (.role b (InviteCreateParams$Role/of (impl/enum-name role)))
    (when projects (.projects b ^java.util.List (mapv ->invite-project projects))) (.build b)))
(defn- ->invite-list-params ^InviteListParams [{:keys [after limit]}]
  (let [b (InviteListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn- invite-project->map [^Invite$Project p] {:id (.id p) :role (impl/->keyword (.role p))})
(defn- invite->map [^Invite i]
  (cond-> {:id (.id i) :created-at (.createdAt i) :email (.email i) :projects (mapv invite-project->map (.projects i)) :role (impl/->keyword (.role i)) :status (impl/->keyword (.status i))}
    (.isPresent (.acceptedAt i)) (assoc :accepted-at (impl/opt-get (.acceptedAt i)))
    (.isPresent (.expiresAt i)) (assoc :expires-at (impl/opt-get (.expiresAt i)))))
(defn invite-create [^OpenAIClient client req] (impl/with-api-errors (let [^InviteService s (.invites (organization client))] (invite->map (.create s (->invite-create-params req))))))
(defn invite-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^InviteService s (.invites (organization client))] (invite->map (.retrieve s id)))))
(defn invite-list
  ([^OpenAIClient client] (invite-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^InviteService s (.invites (organization client)) ^InviteListPage p (.list s (->invite-list-params opts))] (mapv invite->map (impl/all-pages p))))))
(defn invite-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^InviteService s (.invites (organization client)) ^InviteDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
(defadmin "project" [:projects] [:create :retrieve :update :list :archive])
(defadmin "role" [:roles] [:create :retrieve :update :list :delete])
(defadmin "spend-alert" [:spendAlerts] [:create :retrieve :update :list :delete])
(defadmin "user" [:users] [:retrieve :update :list :delete])
(defadmin "group-role" [:groups :roles] [:create :retrieve :list :delete])
(defadmin "group-user" [:groups :users] [:create :retrieve :list :delete])
(defadmin "user-role" [:users :roles] [:create :retrieve :list :delete])
(defadmin "usage" [:usage]
  [:audioSpeeches :audioTranscriptions :codeInterpreterSessions :completions
   :costs :embeddings :fileSearchCalls :images :moderations :vectorStores :webSearchCalls])
