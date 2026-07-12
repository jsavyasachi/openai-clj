# Admin

`openai.admin` covers organization resources and usage. `openai.admin.projects`
covers project-scoped API keys, service accounts, limits, permissions, users,
groups, roles, retention, spend alerts, and certificates.

Typed Admin wrappers use positional resource IDs and a trailing kebab-case map
for optional body or query values. Admin groups not yet migrated to typed
interop still accept one kebab-case params map containing path, query, and body
values required by the SDK operation.

```clojure
(admin/project-create client {:name "research"})
(admin/group-role-create client "group_..." {:role-id "role_..."})
(admin/group-user-retrieve client "group_..." "user_...")
(admin-projects/user-list client {:project-id "proj_..." :limit 20})
```
