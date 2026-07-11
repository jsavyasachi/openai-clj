# Vector Stores

`openai.vector-stores` covers store CRUD/search, files, file content, and file
batches. List operations automatically traverse every SDK page.

```clojure
(vector-stores/create client {:name "manuals" :file-ids ["file_..."]})
(vector-stores/search client "vs_..." {:query "installation"})
```
