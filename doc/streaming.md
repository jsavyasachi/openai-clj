# Streaming

```clojure
(openai/stream
 client
 {:model "gpt-5.2" :input "Count to three."}
 prn)
;; prints normalized event maps and returns the concatenated output text

(openai/stream-text
 client
 {:model "gpt-5.2" :input "Count to three."}
 print)
;; prints text deltas and returns the concatenated output text

(openai/retrieve-streaming client "resp_123" prn)
;; resumes streaming an existing background response
```

