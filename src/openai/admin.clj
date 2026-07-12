(ns openai.admin
  "Organization-level OpenAI Admin API wrappers."
  (:require [clojure.string :as str] [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue Page)
           (com.openai.models.admin.organization.adminapikeys AdminApiKey AdminApiKey$Owner AdminApiKeyCreateParams AdminApiKeyCreateResponse AdminApiKeyDeleteResponse AdminApiKeyListPage AdminApiKeyListParams AdminApiKeyListParams$Order)
           (com.openai.models.admin.organization.auditlogs AuditLogListPage AuditLogListParams AuditLogListParams$EffectiveAt AuditLogListParams$EventType AuditLogListResponse)
           (com.openai.models.admin.organization.certificates Certificate Certificate$CertificateDetails CertificateActivatePage CertificateActivateParams CertificateActivateResponse CertificateActivateResponse$CertificateDetails CertificateCreateParams CertificateDeactivatePage CertificateDeactivateParams CertificateDeactivateResponse CertificateDeactivateResponse$CertificateDetails CertificateDeleteResponse CertificateListPage CertificateListParams CertificateListParams$Order CertificateListResponse CertificateListResponse$CertificateDetails CertificateRetrieveParams CertificateRetrieveParams$Include CertificateUpdateParams)
           (com.openai.models.admin.organization.dataretention DataRetentionUpdateParams DataRetentionUpdateParams$RetentionType OrganizationDataRetention)
           (com.openai.models.admin.organization.groups Group GroupCreateParams GroupDeleteResponse GroupListPage GroupListParams GroupListParams$Order GroupUpdateParams GroupUpdateResponse)
           (com.openai.models.admin.organization.invites Invite Invite$Project InviteCreateParams InviteCreateParams$Project InviteCreateParams$Project$Role InviteCreateParams$Role InviteDeleteResponse InviteListPage InviteListParams)
           (com.openai.models.admin.organization.projects ProjectCreateParams)
           (com.openai.models.admin.organization.roles Role RoleCreateParams RoleDeleteResponse RoleListPage RoleListParams RoleListParams$Order RoleUpdateParams)
           (com.openai.models.admin.organization.users OrganizationUser UserDeleteResponse UserListPage UserListParams UserUpdateParams)
           (com.openai.services.blocking.admin OrganizationService)
           (com.openai.services.blocking.admin.organization AdminApiKeyService AuditLogService CertificateService DataRetentionService GroupService InviteService RoleService UserService)
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
(defn- ->group-create-params ^GroupCreateParams [{:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (GroupCreateParams/builder) (.name ^String name) (.build)))
(defn- ->group-update-params ^GroupUpdateParams [^String id {:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (GroupUpdateParams/builder) (.groupId id) (.name ^String name) (.build)))
(defn- ->group-list-params ^GroupListParams [{:keys [after limit order]}]
  (let [b (GroupListParams/builder)]
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (GroupListParams$Order/of (impl/enum-name order))))
    (.build b)))
(defn- group->map [^Group g]
  {:id (.id g) :created-at (.createdAt g) :group-type (impl/->keyword (.groupType g))
   :is-scim-managed (.isScimManaged g) :name (.name g)})
(defn- group-update->map [^GroupUpdateResponse g]
  {:id (.id g) :created-at (.createdAt g) :is-scim-managed (.isScimManaged g) :name (.name g)})
(defn group-create [^OpenAIClient client req]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group->map (.create s (->group-create-params req))))))
(defn group-retrieve [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group->map (.retrieve s id)))))
(defn group-update [^OpenAIClient client ^String id req]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client))] (group-update->map (.update s (->group-update-params id req))))))
(defn group-list
  ([^OpenAIClient client] (group-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^GroupService s (.groups (organization client)) ^GroupListPage p (.list s (->group-list-params opts))] (mapv group->map (impl/all-pages p))))))
(defn group-delete [^OpenAIClient client ^String id]
  (impl/with-api-errors (let [^GroupService s (.groups (organization client)) ^GroupDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
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
(defn- ->role-create-params ^RoleCreateParams [{:keys [role-name permissions description]}]
  (when-not role-name (impl/missing-key! :role-name))
  (when-not permissions (impl/missing-key! :permissions))
  (let [b (RoleCreateParams/builder)] (.roleName b ^String role-name) (.permissions b ^java.util.List permissions)
    (when description (.description b ^String description)) (.build b)))
(defn- ->role-update-params ^RoleUpdateParams [^String id {:keys [description permissions]}]
  (let [b (RoleUpdateParams/builder)] (.roleId b id)
    (when description (.description b ^String description))
    (when permissions (.permissions b ^java.util.List permissions)) (.build b)))
(defn- ->role-list-params ^RoleListParams [{:keys [after limit order]}]
  (let [b (RoleListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- role->map [^Role r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))))
(defn role-create [^OpenAIClient client req] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.create s (->role-create-params req))))))
(defn role-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.retrieve s id)))))
(defn role-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^RoleService s (.roles (organization client))] (role->map (.update s (->role-update-params id opts))))))
(defn role-list
  ([^OpenAIClient client] (role-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^RoleService s (.roles (organization client)) ^RoleListPage p (.list s (->role-list-params opts))] (mapv role->map (impl/all-pages p))))))
(defn role-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^RoleService s (.roles (organization client)) ^RoleDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))
(defadmin "spend-alert" [:spendAlerts] [:create :retrieve :update :list :delete])
(defn- ->user-update-params ^UserUpdateParams [^String id {:keys [developer-persona role role-id technical-level]}]
  (let [b (UserUpdateParams/builder)] (.userId b id)
    (when developer-persona (.developerPersona b ^String developer-persona))
    (when role (.role b ^String role)) (when role-id (.roleId b ^String role-id))
    (when technical-level (.technicalLevel b ^String technical-level)) (.build b)))
(defn- ->user-list-params ^UserListParams [{:keys [after limit]}]
  (let [b (UserListParams/builder)] (when after (.after b ^String after)) (when limit (.limit b (long limit))) (.build b)))
(defn- organization-user->map [^OrganizationUser u]
  (cond-> {:id (.id u) :added-at (.addedAt u)}
    (.isPresent (.apiKeyLastUsedAt u)) (assoc :api-key-last-used-at (impl/opt-get (.apiKeyLastUsedAt u)))
    (.isPresent (.created u)) (assoc :created (impl/opt-get (.created u)))
    (.isPresent (.developerPersona u)) (assoc :developer-persona (impl/opt-get (.developerPersona u)))
    (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))
    (.isPresent (.isDefault u)) (assoc :is-default (impl/opt-get (.isDefault u)))
    (.isPresent (.isScaleTierAuthorizedPurchaser u)) (assoc :is-scale-tier-authorized-purchaser (impl/opt-get (.isScaleTierAuthorizedPurchaser u)))
    (.isPresent (.isScimManaged u)) (assoc :is-scim-managed (impl/opt-get (.isScimManaged u)))
    (.isPresent (.isServiceAccount u)) (assoc :is-service-account (impl/opt-get (.isServiceAccount u)))
    (.isPresent (.name u)) (assoc :name (impl/opt-get (.name u)))
    (.isPresent (.role u)) (assoc :role (impl/opt-get (.role u)))
    (.isPresent (.technicalLevel u)) (assoc :technical-level (impl/opt-get (.technicalLevel u)))))
(defn user-retrieve [^OpenAIClient client ^String id] (impl/with-api-errors (let [^UserService s (.users (organization client))] (organization-user->map (.retrieve s id)))))
(defn user-update [^OpenAIClient client ^String id opts] (impl/with-api-errors (let [^UserService s (.users (organization client))] (organization-user->map (.update s (->user-update-params id opts))))))
(defn user-list
  ([^OpenAIClient client] (user-list client {}))
  ([^OpenAIClient client opts] (impl/with-api-errors (let [^UserService s (.users (organization client)) ^UserListPage p (.list s (->user-list-params opts))] (mapv organization-user->map (impl/all-pages p))))))
(defn user-delete [^OpenAIClient client ^String id] (impl/with-api-errors (let [^UserService s (.users (organization client)) ^UserDeleteResponse r (.delete s id)] {:id (.id r) :deleted (.deleted r)})))

(defn- ->group-role-create-params ^com.openai.models.admin.organization.groups.roles.RoleCreateParams [^String group-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.groups.roles.RoleCreateParams/builder) (.groupId group-id) (.roleId ^String role-id) (.build)))
(defn- ->group-role-retrieve-params ^com.openai.models.admin.organization.groups.roles.RoleRetrieveParams [^String group-id ^String role-id]
  (-> (com.openai.models.admin.organization.groups.roles.RoleRetrieveParams/builder) (.groupId group-id) (.roleId role-id) (.build)))
(defn- ->group-role-list-params ^com.openai.models.admin.organization.groups.roles.RoleListParams [^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.groups.roles.RoleListParams/builder)] (.groupId b group-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.groups.roles.RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- group-role-retrieve->map [^com.openai.models.admin.organization.groups.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- group-role-list->map [^com.openai.models.admin.organization.groups.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- group-role-create->map [^com.openai.models.admin.organization.groups.roles.RoleCreateResponse r]
  (let [^com.openai.models.admin.organization.groups.roles.RoleCreateResponse$Group g (.group r)]
    {:group {:id (.id g) :created-at (.createdAt g) :name (.name g) :scim-managed (.scimManaged g)} :role (role->map (.role r))}))
(defn group-role-create [^OpenAIClient client ^String group-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client)))] (group-role-create->map (.create s (->group-role-create-params group-id req))))))
(defn group-role-retrieve [^OpenAIClient client ^String group-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client)))] (group-role-retrieve->map (.retrieve s (->group-role-retrieve-params group-id role-id))))))
(defn group-role-list
  ([^OpenAIClient client ^String group-id] (group-role-list client group-id {}))
  ([^OpenAIClient client ^String group-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client))) ^com.openai.models.admin.organization.groups.roles.RoleListPage p (.list s (->group-role-list-params group-id opts))] (mapv group-role-list->map (impl/all-pages p))))))
(defn group-role-delete [^OpenAIClient client ^String group-id ^String role-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.RoleService s (.roles (.groups (organization client))) p (-> (com.openai.models.admin.organization.groups.roles.RoleDeleteParams/builder) (.groupId group-id) (.roleId role-id) (.build)) ^com.openai.models.admin.organization.groups.roles.RoleDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))

(defn- ->group-user-create-params ^com.openai.models.admin.organization.groups.users.UserCreateParams [^String group-id {:keys [user-id]}]
  (when-not user-id (impl/missing-key! :user-id))
  (-> (com.openai.models.admin.organization.groups.users.UserCreateParams/builder) (.groupId group-id) (.userId ^String user-id) (.build)))
(defn- ->group-user-retrieve-params ^com.openai.models.admin.organization.groups.users.UserRetrieveParams [^String group-id ^String user-id]
  (-> (com.openai.models.admin.organization.groups.users.UserRetrieveParams/builder) (.groupId group-id) (.userId user-id) (.build)))
(defn- ->group-user-list-params ^com.openai.models.admin.organization.groups.users.UserListParams [^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.groups.users.UserListParams/builder)] (.groupId b group-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.groups.users.UserListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- group-user-retrieve->map [^com.openai.models.admin.organization.groups.users.UserRetrieveResponse u]
  (cond-> {:id (.id u) :name (.name u) :user-type (impl/->keyword (.userType u))}
    (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))
    (.isPresent (.isServiceAccount u)) (assoc :is-service-account (impl/opt-get (.isServiceAccount u)))
    (.isPresent (.picture u)) (assoc :picture (impl/opt-get (.picture u)))))
(defn- group-user-list->map [^com.openai.models.admin.organization.groups.users.OrganizationGroupUser u]
  (cond-> {:id (.id u) :name (.name u)} (.isPresent (.email u)) (assoc :email (impl/opt-get (.email u)))))
(defn group-user-create [^OpenAIClient client ^String group-id req] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) ^com.openai.models.admin.organization.groups.users.UserCreateResponse r (.create s (->group-user-create-params group-id req))] {:group-id (.groupId r) :user-id (.userId r)})))
(defn group-user-retrieve [^OpenAIClient client ^String group-id ^String user-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client)))] (group-user-retrieve->map (.retrieve s (->group-user-retrieve-params group-id user-id))))))
(defn group-user-list
  ([^OpenAIClient client ^String group-id] (group-user-list client group-id {}))
  ([^OpenAIClient client ^String group-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) ^com.openai.models.admin.organization.groups.users.UserListPage p (.list s (->group-user-list-params group-id opts))] (mapv group-user-list->map (impl/all-pages p))))))
(defn group-user-delete [^OpenAIClient client ^String group-id ^String user-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.groups.UserService s (.users (.groups (organization client))) p (-> (com.openai.models.admin.organization.groups.users.UserDeleteParams/builder) (.groupId group-id) (.userId user-id) (.build)) ^com.openai.models.admin.organization.groups.users.UserDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))

(defn- ->user-role-create-params ^com.openai.models.admin.organization.users.roles.RoleCreateParams [^String user-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.users.roles.RoleCreateParams/builder) (.userId user-id) (.roleId ^String role-id) (.build)))
(defn- ->user-role-retrieve-params ^com.openai.models.admin.organization.users.roles.RoleRetrieveParams [^String user-id ^String role-id]
  (-> (com.openai.models.admin.organization.users.roles.RoleRetrieveParams/builder) (.userId user-id) (.roleId role-id) (.build)))
(defn- ->user-role-list-params ^com.openai.models.admin.organization.users.roles.RoleListParams [^String user-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.users.roles.RoleListParams/builder)] (.userId b user-id)
    (when after (.after b ^String after)) (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.users.roles.RoleListParams$Order/of (impl/enum-name order)))) (.build b)))
(defn- user-role-retrieve->map [^com.openai.models.admin.organization.users.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn- user-role-list->map [^com.openai.models.admin.organization.users.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r)) :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))
(defn user-role-create [^OpenAIClient client ^String user-id req]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) ^com.openai.models.admin.organization.users.roles.RoleCreateResponse r (.create s (->user-role-create-params user-id req))] {:role (role->map (.role r)) :user (organization-user->map (.user r))})))
(defn user-role-retrieve [^OpenAIClient client ^String user-id ^String role-id] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client)))] (user-role-retrieve->map (.retrieve s (->user-role-retrieve-params user-id role-id))))))
(defn user-role-list
  ([^OpenAIClient client ^String user-id] (user-role-list client user-id {}))
  ([^OpenAIClient client ^String user-id opts] (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) ^com.openai.models.admin.organization.users.roles.RoleListPage p (.list s (->user-role-list-params user-id opts))] (mapv user-role-list->map (impl/all-pages p))))))
(defn user-role-delete [^OpenAIClient client ^String user-id ^String role-id]
  (impl/with-api-errors (let [^com.openai.services.blocking.admin.organization.users.RoleService s (.roles (.users (organization client))) p (-> (com.openai.models.admin.organization.users.roles.RoleDeleteParams/builder) (.userId user-id) (.roleId role-id) (.build)) ^com.openai.models.admin.organization.users.roles.RoleDeleteResponse r (.delete s p)] {:deleted (.deleted r)})))
(defadmin "usage" [:usage]
  [:audioSpeeches :audioTranscriptions :codeInterpreterSessions :completions
   :costs :embeddings :fileSearchCalls :images :moderations :vectorStores :webSearchCalls])
