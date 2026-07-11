# Webhooks

Configure the signing secret on the client, then pass the unmodified payload
and request headers to `verify-signature` or `unwrap`.

```clojure
(def webhook-client (openai/client {:webhook-secret "whsec_..."}))
(webhooks/verify-signature webhook-client raw-body headers)
(webhooks/unwrap webhook-client raw-body headers)
```
