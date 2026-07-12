# Admin

`openai.admin` covers organization resources and usage. `openai.admin.projects`
covers project-scoped API keys, service accounts, limits, permissions, users,
groups, roles, retention, spend alerts, and certificates.

Admin wrappers take positional resource IDs (project, group, user, role, and
so on) followed by a trailing kebab-case map for optional body or query values,
matching the rest of the library.

```clojure
(admin/project-create client {:name "research"})
(admin/group-role-create client "group_..." {:role-id "role_..."})
(admin/group-user-retrieve client "group_..." "user_...")
(admin-projects/user-list client "proj_..." {:limit 20})
```
