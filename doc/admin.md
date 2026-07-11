# Admin

`openai.admin` covers organization resources and usage. `openai.admin.projects`
covers project-scoped API keys, service accounts, limits, permissions, users,
groups, roles, retention, spend alerts, and certificates.

Admin functions accept one kebab-case params map containing path, query, and
body values required by the SDK operation.

```clojure
(admin/project-create client {:name "research"})
(admin-projects/user-list client {:project-id "proj_..." :limit 20})
```
