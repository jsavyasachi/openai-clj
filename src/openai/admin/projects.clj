(ns openai.admin.projects
  "Project-scoped OpenAI Admin API wrappers."
  (:require [openai.admin :as admin]
            [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.admin.organization.projects.apikeys ApiKeyDeleteParams ApiKeyDeleteResponse ApiKeyListPage ApiKeyListParams ApiKeyRetrieveParams ProjectApiKey ProjectApiKey$Owner ProjectApiKey$Owner$ServiceAccount ProjectApiKey$Owner$User)
           (com.openai.models.admin.organization.projects.certificates CertificateActivatePage CertificateActivateParams CertificateActivateResponse CertificateActivateResponse$CertificateDetails CertificateDeactivatePage CertificateDeactivateParams CertificateDeactivateResponse CertificateDeactivateResponse$CertificateDetails CertificateListPage CertificateListParams CertificateListParams$Order CertificateListResponse CertificateListResponse$CertificateDetails)
           (com.openai.models.admin.organization.projects.dataretention DataRetentionRetrieveParams DataRetentionUpdateParams DataRetentionUpdateParams$RetentionType ProjectDataRetention)
           (com.openai.models.admin.organization.projects.groups GroupCreateParams GroupDeleteParams GroupDeleteResponse GroupListPage GroupListParams GroupListParams$Order GroupRetrieveParams GroupRetrieveParams$GroupType ProjectGroup)
           (com.openai.models.admin.organization.roles Role)
           (com.openai.models.admin.organization.users OrganizationUser)
           (com.openai.services.blocking.admin OrganizationService)
           (com.openai.services.blocking.admin.organization ProjectService)
           (com.openai.services.blocking.admin.organization.projects ApiKeyService CertificateService DataRetentionService GroupService)
           (com.openai.services.blocking.admin.organization.projects.groups RoleService)))

(set! *warn-on-reflection* true)

(defn- operation-name [prefix action]
  (symbol (str prefix "-" (-> action name
                               (clojure.string/replace #"([a-z])([A-Z])" "$1-$2")
                               clojure.string/lower-case))))
(defmacro ^:private defproject [prefix path actions]
  `(do ~@(for [action actions]
           `(defn ~(operation-name prefix action) [client# params#]
              (admin/request client# ~(into [:projects] path) ~action params#)))))

(defn- projects-service ^ProjectService [^OpenAIClient client]
  (let [^OrganizationService organization (.organization (.admin client))]
    (.projects organization)))

;; API keys

(defn- ->api-key-retrieve-params ^ApiKeyRetrieveParams [^String project-id ^String key-id]
  (-> (ApiKeyRetrieveParams/builder) (.projectId project-id) (.apiKeyId key-id) (.build)))

(defn- ->api-key-list-params ^ApiKeyListParams [^String project-id {:keys [after limit]}]
  (let [b (ApiKeyListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (.build b)))

(defn- api-key-service-account->map [^ProjectApiKey$Owner$ServiceAccount a]
  {:id (.id a) :created-at (.createdAt a) :name (.name a) :role (.role a)})

(defn- api-key-user->map [^ProjectApiKey$Owner$User u]
  {:id (.id u) :created-at (.createdAt u) :email (.email u) :name (.name u) :role (.role u)})

(defn- api-key-owner->map [^ProjectApiKey$Owner o]
  (cond-> {}
    (.isPresent (.serviceAccount o)) (assoc :service-account (api-key-service-account->map (impl/opt-get (.serviceAccount o))))
    (.isPresent (.type o)) (assoc :type (impl/->keyword (impl/opt-get (.type o))))
    (.isPresent (.user o)) (assoc :user (api-key-user->map (impl/opt-get (.user o))))))

(defn- project-api-key->map [^ProjectApiKey k]
  (cond-> {:id (.id k) :created-at (.createdAt k) :name (.name k)
           :owner (api-key-owner->map (.owner k)) :redacted-value (.redactedValue k)}
    (.isPresent (.lastUsedAt k)) (assoc :last-used-at (impl/opt-get (.lastUsedAt k)))))

(defn api-key-retrieve [^OpenAIClient client ^String project-id ^String key-id]
  (impl/with-api-errors
    (let [^ApiKeyService svc (.apiKeys (projects-service client))]
      (project-api-key->map (.retrieve svc (->api-key-retrieve-params project-id key-id))))))

(defn api-key-list
  ([^OpenAIClient client ^String project-id] (api-key-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^ApiKeyService svc (.apiKeys (projects-service client))
           ^ApiKeyListPage page (.list svc (->api-key-list-params project-id opts))]
       (mapv project-api-key->map (impl/all-pages page))))))

(defn api-key-delete [^OpenAIClient client ^String project-id ^String key-id]
  (impl/with-api-errors
    (let [^ApiKeyService svc (.apiKeys (projects-service client))
          p (-> (ApiKeyDeleteParams/builder) (.projectId project-id) (.apiKeyId key-id) (.build))
          ^ApiKeyDeleteResponse r (.delete svc p)]
      {:id (.id r) :deleted (.deleted r)})))

;; Certificates

(defn- ->certificate-list-params ^CertificateListParams [^String project-id {:keys [after limit order]}]
  (let [b (CertificateListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (CertificateListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- certificate-list-details->map [^CertificateListResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-list->map [^CertificateListResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-list-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn- ->certificate-activate-params ^CertificateActivateParams [^String project-id {:keys [certificate-ids]}]
  (when-not certificate-ids (impl/missing-key! :certificate-ids))
  (-> (CertificateActivateParams/builder) (.projectId project-id)
      (.certificateIds ^java.util.List certificate-ids) (.build)))

(defn- ->certificate-deactivate-params ^CertificateDeactivateParams [^String project-id {:keys [certificate-ids]}]
  (when-not certificate-ids (impl/missing-key! :certificate-ids))
  (-> (CertificateDeactivateParams/builder) (.projectId project-id)
      (.certificateIds ^java.util.List certificate-ids) (.build)))

(defn- certificate-activate-details->map [^CertificateActivateResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-deactivate-details->map [^CertificateDeactivateResponse$CertificateDetails d]
  (cond-> {}
    (.isPresent (.expiresAt d)) (assoc :expires-at (impl/opt-get (.expiresAt d)))
    (.isPresent (.validAt d)) (assoc :valid-at (impl/opt-get (.validAt d)))))

(defn- certificate-activate->map [^CertificateActivateResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-activate-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn- certificate-deactivate->map [^CertificateDeactivateResponse c]
  (cond-> {:id (.id c) :active (.active c)
           :certificate-details (certificate-deactivate-details->map (.certificateDetails c))
           :created-at (.createdAt c)}
    (.isPresent (.name c)) (assoc :name (impl/opt-get (.name c)))))

(defn certificate-list
  ([^OpenAIClient client ^String project-id] (certificate-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^CertificateService svc (.certificates (projects-service client))
           ^CertificateListPage page (.list svc (->certificate-list-params project-id opts))]
       (mapv certificate-list->map (impl/all-pages page))))))

(defn certificate-activate [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^CertificateService svc (.certificates (projects-service client))
          ^CertificateActivatePage page (.activate svc (->certificate-activate-params project-id req))]
      (mapv certificate-activate->map (impl/all-pages page)))))

(defn certificate-deactivate [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^CertificateService svc (.certificates (projects-service client))
          ^CertificateDeactivatePage page (.deactivate svc (->certificate-deactivate-params project-id req))]
      (mapv certificate-deactivate->map (impl/all-pages page)))))

;; Data retention

(defn- ->data-retention-retrieve-params ^DataRetentionRetrieveParams [^String project-id]
  (-> (DataRetentionRetrieveParams/builder) (.projectId project-id) (.build)))

(defn- ->data-retention-update-params ^DataRetentionUpdateParams [^String project-id {:keys [retention-type]}]
  (when-not retention-type (impl/missing-key! :retention-type))
  (-> (DataRetentionUpdateParams/builder) (.projectId project-id)
      (.retentionType (DataRetentionUpdateParams$RetentionType/of (impl/enum-name retention-type)))
      (.build)))

(defn- data-retention->map [^ProjectDataRetention d]
  {:type (impl/->keyword (.type d))})

(defn data-retention-retrieve [^OpenAIClient client ^String project-id]
  (impl/with-api-errors
    (let [^DataRetentionService svc (.dataRetention (projects-service client))]
      (data-retention->map (.retrieve svc (->data-retention-retrieve-params project-id))))))

(defn data-retention-update [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^DataRetentionService svc (.dataRetention (projects-service client))]
      (data-retention->map (.update svc (->data-retention-update-params project-id req))))))

;; Groups

(defn- ->group-create-params ^GroupCreateParams [^String project-id {:keys [group-id role]}]
  (when-not group-id (impl/missing-key! :group-id))
  (when-not role (impl/missing-key! :role))
  (-> (GroupCreateParams/builder) (.projectId project-id) (.groupId ^String group-id)
      (.role ^String role) (.build)))

(defn- ->group-retrieve-params ^GroupRetrieveParams [^String project-id ^String group-id {:keys [group-type]}]
  (let [b (GroupRetrieveParams/builder)]
    (.projectId b project-id)
    (.groupId b group-id)
    (when group-type (.groupType b (GroupRetrieveParams$GroupType/of (impl/enum-name group-type))))
    (.build b)))

(defn- ->group-list-params ^GroupListParams [^String project-id {:keys [after limit order]}]
  (let [b (GroupListParams/builder)]
    (.projectId b project-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (GroupListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- project-group->map [^ProjectGroup g]
  {:created-at (.createdAt g) :group-id (.groupId g) :group-name (.groupName g)
   :group-type (impl/->keyword (.groupType g)) :project-id (.projectId g)})

(defn group-create [^OpenAIClient client ^String project-id req]
  (impl/with-api-errors
    (let [^GroupService svc (.groups (projects-service client))]
      (project-group->map (.create svc (->group-create-params project-id req))))))

(defn group-retrieve
  ([^OpenAIClient client ^String project-id ^String group-id]
   (group-retrieve client project-id group-id {}))
  ([^OpenAIClient client ^String project-id ^String group-id opts]
   (impl/with-api-errors
     (let [^GroupService svc (.groups (projects-service client))]
       (project-group->map (.retrieve svc (->group-retrieve-params project-id group-id opts)))))))

(defn group-list
  ([^OpenAIClient client ^String project-id] (group-list client project-id {}))
  ([^OpenAIClient client ^String project-id opts]
   (impl/with-api-errors
     (let [^GroupService svc (.groups (projects-service client))
           ^GroupListPage page (.list svc (->group-list-params project-id opts))]
       (mapv project-group->map (impl/all-pages page))))))

(defn group-delete [^OpenAIClient client ^String project-id ^String group-id]
  (impl/with-api-errors
    (let [^GroupService svc (.groups (projects-service client))
          p (-> (GroupDeleteParams/builder) (.projectId project-id) (.groupId group-id) (.build))
          ^GroupDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

;; Project group roles

(defn- ->group-role-create-params ^com.openai.models.admin.organization.projects.groups.roles.RoleCreateParams [^String project-id ^String group-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.projects.groups.roles.RoleCreateParams/builder)
      (.projectId project-id) (.groupId group-id) (.roleId ^String role-id) (.build)))

(defn- ->group-role-retrieve-params ^com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveParams [^String project-id ^String group-id ^String role-id]
  (-> (com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveParams/builder)
      (.projectId project-id) (.groupId group-id) (.roleId role-id) (.build)))

(defn- ->group-role-list-params ^com.openai.models.admin.organization.projects.groups.roles.RoleListParams [^String project-id ^String group-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.projects.groups.roles.RoleListParams/builder)]
    (.projectId b project-id) (.groupId b group-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.projects.groups.roles.RoleListParams$Order/of (impl/enum-name order))))
    (.build b)))

(defn- role->map [^Role r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))))

(defn- group-role-retrieve->map [^com.openai.models.admin.organization.projects.groups.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- group-role-list->map [^com.openai.models.admin.organization.projects.groups.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- group-role-create->map [^com.openai.models.admin.organization.projects.groups.roles.RoleCreateResponse r]
  (let [^com.openai.models.admin.organization.projects.groups.roles.RoleCreateResponse$Group g (.group r)]
    {:group {:id (.id g) :created-at (.createdAt g) :name (.name g) :scim-managed (.scimManaged g)}
     :role (role->map (.role r))}))

(defn group-role-create [^OpenAIClient client ^String project-id ^String group-id req]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))]
      (group-role-create->map (.create svc (->group-role-create-params project-id group-id req))))))

(defn group-role-retrieve [^OpenAIClient client ^String project-id ^String group-id ^String role-id]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))]
      (group-role-retrieve->map (.retrieve svc (->group-role-retrieve-params project-id group-id role-id))))))

(defn group-role-list
  ([^OpenAIClient client ^String project-id ^String group-id]
   (group-role-list client project-id group-id {}))
  ([^OpenAIClient client ^String project-id ^String group-id opts]
   (impl/with-api-errors
     (let [^RoleService svc (.roles (.groups (projects-service client)))
           ^com.openai.models.admin.organization.projects.groups.roles.RoleListPage page
           (.list svc (->group-role-list-params project-id group-id opts))]
       (mapv group-role-list->map (impl/all-pages page))))))

(defn group-role-delete [^OpenAIClient client ^String project-id ^String group-id ^String role-id]
  (impl/with-api-errors
    (let [^RoleService svc (.roles (.groups (projects-service client)))
          p (-> (com.openai.models.admin.organization.projects.groups.roles.RoleDeleteParams/builder)
                (.projectId project-id) (.groupId group-id) (.roleId role-id) (.build))
          ^com.openai.models.admin.organization.projects.groups.roles.RoleDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

;; Project user roles

(defn- ->user-role-create-params ^com.openai.models.admin.organization.projects.users.roles.RoleCreateParams [^String project-id ^String user-id {:keys [role-id]}]
  (when-not role-id (impl/missing-key! :role-id))
  (-> (com.openai.models.admin.organization.projects.users.roles.RoleCreateParams/builder)
      (.projectId project-id) (.userId user-id) (.roleId ^String role-id) (.build)))

(defn- ->user-role-retrieve-params ^com.openai.models.admin.organization.projects.users.roles.RoleRetrieveParams [^String project-id ^String user-id ^String role-id]
  (-> (com.openai.models.admin.organization.projects.users.roles.RoleRetrieveParams/builder)
      (.projectId project-id) (.userId user-id) (.roleId role-id) (.build)))

(defn- ->user-role-list-params ^com.openai.models.admin.organization.projects.users.roles.RoleListParams [^String project-id ^String user-id {:keys [after limit order]}]
  (let [b (com.openai.models.admin.organization.projects.users.roles.RoleListParams/builder)]
    (.projectId b project-id) (.userId b user-id)
    (when after (.after b ^String after))
    (when limit (.limit b (long limit)))
    (when order (.order b (com.openai.models.admin.organization.projects.users.roles.RoleListParams$Order/of (impl/enum-name order))))
    (.build b)))

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

(defn- user-role-retrieve->map [^com.openai.models.admin.organization.projects.users.roles.RoleRetrieveResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn- user-role-list->map [^com.openai.models.admin.organization.projects.users.roles.RoleListResponse r]
  (cond-> {:id (.id r) :name (.name r) :permissions (vec (.permissions r))
           :predefined-role (.predefinedRole r) :resource-type (.resourceType r)}
    (.isPresent (.createdAt r)) (assoc :created-at (impl/opt-get (.createdAt r)))
    (.isPresent (.createdBy r)) (assoc :created-by (impl/opt-get (.createdBy r)))
    (.isPresent (.description r)) (assoc :description (impl/opt-get (.description r)))
    (.isPresent (.updatedAt r)) (assoc :updated-at (impl/opt-get (.updatedAt r)))))

(defn user-role-create [^OpenAIClient client ^String project-id ^String user-id req]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))
          ^com.openai.models.admin.organization.projects.users.roles.RoleCreateResponse r
          (.create svc (->user-role-create-params project-id user-id req))]
      {:role (role->map (.role r)) :user (organization-user->map (.user r))})))

(defn user-role-retrieve [^OpenAIClient client ^String project-id ^String user-id ^String role-id]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))]
      (user-role-retrieve->map (.retrieve svc (->user-role-retrieve-params project-id user-id role-id))))))

(defn user-role-list
  ([^OpenAIClient client ^String project-id ^String user-id]
   (user-role-list client project-id user-id {}))
  ([^OpenAIClient client ^String project-id ^String user-id opts]
   (impl/with-api-errors
     (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
           (.roles (.users (projects-service client)))
           ^com.openai.models.admin.organization.projects.users.roles.RoleListPage page
           (.list svc (->user-role-list-params project-id user-id opts))]
       (mapv user-role-list->map (impl/all-pages page))))))

(defn user-role-delete [^OpenAIClient client ^String project-id ^String user-id ^String role-id]
  (impl/with-api-errors
    (let [^com.openai.services.blocking.admin.organization.projects.users.RoleService svc
          (.roles (.users (projects-service client)))
          p (-> (com.openai.models.admin.organization.projects.users.roles.RoleDeleteParams/builder)
                (.projectId project-id) (.userId user-id) (.roleId role-id) (.build))
          ^com.openai.models.admin.organization.projects.users.roles.RoleDeleteResponse r (.delete svc p)]
      {:deleted (.deleted r)})))

(defproject "hosted-tool-permission" [:hostedToolPermissions] [:retrieve :update])
(defproject "model-permission" [:modelPermissions] [:retrieve :update :delete])
(defproject "rate-limit" [:rateLimits] [:listRateLimits :updateRateLimit])
(defproject "role" [:roles] [:create :retrieve :update :list :delete])
(defproject "service-account" [:serviceAccounts] [:create :retrieve :update :list :delete])
(defproject "spend-alert" [:spendAlerts] [:create :retrieve :update :list :delete])
(defproject "user" [:users] [:create :retrieve :update :list :delete])
