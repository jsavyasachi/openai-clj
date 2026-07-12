(ns openai.admin-test
  (:require [clojure.test :refer [deftest is]]
            [openai.admin :as admin]
            [openai.admin.projects :as projects]
            [openai.impl :as impl])
  (:import (com.openai.models.admin.organization.adminapikeys AdminApiKeyCreateParams)
           (com.openai.models.admin.organization.groups Group Group$Builder GroupCreateParams)
           (com.openai.models.admin.organization.groups.users UserCreateParams)
           (com.openai.models.admin.organization.invites Invite Invite$Builder Invite$Role Invite$Status)
           (com.openai.models.admin.organization.projects Project ProjectCreateParams)
           (com.openai.models.admin.organization.spendalerts OrganizationSpendAlert OrganizationSpendAlert$Currency OrganizationSpendAlert$Interval OrganizationSpendAlert$NotificationChannel)
           (com.openai.models.admin.organization.usage UsageCompletionsParams UsageCompletionsResponse$Data UsageCompletionsResponse$Data$Result$OrganizationUsageCompletionsResult)))
(set! *warn-on-reflection* true)
(deftest translates-project-create
  (let [^ProjectCreateParams p (#'admin/->project-create-params {:name "research"})]
    (is (= "research" (.name p)))))

(deftest converts-project-present-only
  (let [project (-> (Project/builder) (.id "proj_1") (.createdAt 123)
                    (.name "Research") (.status "active") (.build))]
    (is (= {:id "proj_1" :created-at 123 :name "Research" :status "active"}
           (#'admin/project->map project)))))

(deftest converts-spend-alert
  (let [channel (-> (OrganizationSpendAlert$NotificationChannel/builder)
                    (.recipients ["ops@example.com"]) (.type (com.openai.core.JsonValue/from "email")) (.build))
        alert (-> (OrganizationSpendAlert/builder) (.id "alert_1")
                  (.currency (OrganizationSpendAlert$Currency/of "usd"))
                  (.interval (OrganizationSpendAlert$Interval/of "monthly"))
                  (.notificationChannel channel) (.thresholdAmount 1000) (.build))]
    (is (= {:id "alert_1" :currency :usd :interval :monthly
            :notification-channel {:recipients ["ops@example.com"] :type "email"}
            :threshold-amount 1000}
           (#'admin/spend-alert->map alert)))))

(deftest builds-usage-completions-params
  (let [^UsageCompletionsParams p (#'admin/->usage-completions-params
                                    {:start-time 100 :bucket-width :day
                                     :group-by [:model] :batch true})]
    (is (= 100 (.startTime p)))
    (is (= "1d" (str (impl/opt-get (.bucketWidth p)))))
    (is (= ["model"] (mapv str (impl/opt-get (.groupBy p)))))
    (is (true? (impl/opt-get (.batch p))))))

(deftest converts-usage-completions-bucket
  (let [result (-> (UsageCompletionsResponse$Data$Result$OrganizationUsageCompletionsResult/builder)
                   (.inputTokens 10) (.numModelRequests 2) (.outputTokens 4)
                   (.model "gpt-4.1") (.build))
        bucket (-> (UsageCompletionsResponse$Data/builder) (.startTime 100) (.endTime 200)
                   (.addResult result) (.build))]
    (is (= {:start-time 100 :end-time 200
            :results [{:input-tokens 10 :num-model-requests 2 :output-tokens 4
                       :model "gpt-4.1"}]}
           (#'admin/usage-completions-bucket->map bucket)))))

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
