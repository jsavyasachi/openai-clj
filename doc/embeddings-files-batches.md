# Embeddings

```clojure
(openai/create-embeddings
 client
 {:model "text-embedding-3-small"
  :input ["first text" "second text"]
  :dimensions 256})
;; => {:model "text-embedding-3-small"
;;     :embeddings [[0.01 -0.02 ...] [0.03 0.04 ...]]
;;     :usage {:prompt-tokens 8 :total-tokens 8}}
```

### Files And Batches

```clojure
(openai/upload-file client {:file "requests.jsonl" :purpose :batch})
;; :file accepts a Path, string path, byte array, or InputStream
;; (pass :filename with byte-array/stream input);
;; optional :expires-after {:seconds n}
(openai/get-file client "file_123")
(openai/list-files client {:purpose :batch :order :desc :limit 10})
(openai/file-content client "file_123") ;; => byte[]
(openai/delete-file client "file_123")

(openai/create-batch client {:input-file-id "file_123"
                             :endpoint "/v1/responses"})
;; optional :completion-window (default "24h"), :metadata,
;; :output-expires-after {:seconds n}
(openai/get-batch client "batch_123")
(openai/list-batches client {:limit 10})
(openai/cancel-batch client "batch_123")
```

Batch maps carry `:id :status :endpoint :input-file-id :completion-window
:created-at` plus `:output-file-id`, `:error-file-id`, `:request-counts`, and
timestamps when present.

