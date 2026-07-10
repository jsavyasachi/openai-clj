# Function Tools

```clojure
(def weather-tool
  {:type :function
   :name "get_weather"
   :description "Get current weather for a location"
   :strict true
   :parameters {:type "object"
                :properties {:location {:type "string"}}
                :required ["location"]}})

(def first-response
  (openai/create-response
   client
   {:model "gpt-5.2"
    :input "What is the weather in Denver?"
    :tools [weather-tool]
    :tool-choice :auto}))

(def call
  (->> (:output first-response)
       (filter #(= :function-call (:type %)))
       first))

(openai/create-response
 client
 {:model "gpt-5.2"
  :previous-response-id (:id first-response)
  :input [{:type :function-call-output
           :call-id (:call-id call)
           :output {:temperature_f 72 :conditions "sunny"}}]})
```

### Built-In Tools

```clojure
{:tools [{:type :web-search
          :search-context-size :low
          :user-location {:city "Denver"
                          :country "US"
                          :region "CO"
                          :timezone "America/Denver"}
          :allowed-domains ["example.com"]}
         {:type :file-search
          :vector-store-ids ["vs_123"]
          :max-num-results 5
          :filters {:type "eq" :key "kind" :value "docs"}
          :ranking-options {:ranker "auto" :score-threshold 0.5}}
         {:type :file-search
          :vector-store-ids ["vs_123"]
          ;; compound filters compose comparisons with :and/:or and may nest
          :filters {:type :and
                    :filters [{:type :eq :key "kind" :value "docs"}
                              {:type :or
                               :filters [{:type :gte :key "year" :value 2024}
                                         {:type :eq :key "team" :value "core"}]}]}}
         {:type :code-interpreter}
         {:type :code-interpreter :container "cntr_123"}
         {:type :mcp
          :server-label "docs"
          :server-url "https://mcp.example.test"
          :allowed-tools ["search"]
          :require-approval :never
          :headers {"X-Trace" "1"}}]}
```

Tool choice accepts `:auto`, `:required`, `:none`, or
`{:type :function :name "get_weather"}`.

