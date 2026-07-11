(ns openai.admin-test
  (:require [clojure.test :refer [deftest is]]
            [openai.admin :as admin]
            [openai.admin.projects :as projects])
  (:import (com.openai.models.admin.organization.projects ProjectCreateParams)))
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
