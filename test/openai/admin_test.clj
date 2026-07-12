(ns openai.admin-test
  (:require [clojure.test :refer [deftest is]]
            [openai.admin :as admin]
            [openai.admin.projects :as projects]
            [openai.impl :as impl])
  (:import (com.openai.models.admin.organization.adminapikeys AdminApiKeyCreateParams)
           (com.openai.models.admin.organization.groups Group Group$Builder GroupCreateParams)
           (com.openai.models.admin.organization.groups.users UserCreateParams)
           (com.openai.models.admin.organization.invites Invite Invite$Builder Invite$Role Invite$Status)
           (com.openai.models.admin.organization.projects ProjectCreateParams)))
(set! *warn-on-reflection* true)
(deftest translates-project-create
  (let [^ProjectCreateParams p (#'admin/->project-create-params {:name "research"})]
    (is (= "research" (.name p)))))

(deftest exposes-organization-and-project-service-operations
  (doseq [v [#'admin/admin-api-key-create #'admin/audit-log-list
             #'admin/certificate-activate #'admin/group-user-create
             #'admin/project-archive #'admin/usage-completions
             #'projects/api-key-list #'projects/model-permission-update
             #'projects/rate-limit-update-rate-limit
             #'projects/service-account-create #'projects/user-role-delete]]
    (is (fn? @v) (str (:name (meta v)) " should be callable"))))

(deftest builds-admin-api-key-create-params
  (let [^AdminApiKeyCreateParams p (#'admin/->admin-api-key-create-params
                                     {:name "deploy" :expires-in-seconds 3600})]
    (is (= "deploy" (.name p)))
    (is (= 3600 (impl/opt-get (.expiresInSeconds p))))))

(deftest converts-invite-present-only
  (let [^Invite$Builder b (Invite/builder)
        invite (do (.id b "invite_1") (.createdAt b 123) (.email b "dev@example.com")
                   (.projects b (java.util.ArrayList.)) (.role b (Invite$Role/of "reader"))
                   (.status b (Invite$Status/of "pending")) (.build b))]
    (is (= {:id "invite_1" :created-at 123 :email "dev@example.com"
            :projects [] :role :reader :status :pending}
           (#'admin/invite->map invite)))))

(deftest builds-group-create-params
  (let [^GroupCreateParams p (#'admin/->group-create-params {:name "platform"})]
    (is (= "platform" (.name p)))))

(deftest converts-group
  (let [^Group$Builder b (Group/builder)
        group (-> b (.id "group_1") (.createdAt 123)
                  (.groupType (com.openai.models.admin.organization.groups.Group$GroupType/of "custom"))
                  (.isScimManaged false) (.name "Platform") (.build))]
    (is (= {:id "group_1" :created-at 123 :group-type :custom
            :is-scim-managed false :name "Platform"}
           (#'admin/group->map group)))))

(deftest builds-group-user-create-params
  (let [^UserCreateParams p (#'admin/->group-user-create-params
                              "group_1" {:user-id "user_1"})]
    (is (= "group_1" (impl/opt-get (.groupId p))))
    (is (= "user_1" (.userId p)))))
