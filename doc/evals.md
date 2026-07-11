# Evals

`openai.evals` covers eval definitions, runs, and run output items. Custom,
logs, and stored-completions data-source configurations accept Clojure maps.

```clojure
(evals/create client {:name "quality"
                      :data-source-config {:type :custom
                                           :item-schema {:type "object"}}
                      :testing-criteria [{:type :string-check
                                          :name "exact" :operation :eq
                                          :input "{{sample.output_text}}"
                                          :reference "{{item.answer}}"}]})
```
