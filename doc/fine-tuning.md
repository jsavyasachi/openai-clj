# Fine-tuning

`openai.fine-tuning` covers jobs, pause/resume/cancel, events, checkpoints,
checkpoint permissions, and alpha grader execution.

```clojure
(fine-tuning/create-job client {:model "gpt-4.1-mini"
                                :training-file "file_..."})
(fine-tuning/list-events client "ftjob_...")
```
