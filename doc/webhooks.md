# Webhooks

Set the signing secret on the client. Then pass the unchanged payload and
request headers to `verify-signature` or `unwrap`.

```clojure
(def webhook-client (openai/client {:webhook-secret "whsec_..."}))
(webhooks/verify-signature webhook-client raw-body headers)
(webhooks/unwrap webhook-client raw-body headers)
```
