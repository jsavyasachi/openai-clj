(ns openai.admin
  "Organization-level OpenAI Admin API wrappers."
  (:require [clojure.string :as str] [openai.impl :as impl])
  (:import (com.openai.client OpenAIClient)
           (com.openai.core JsonValue Page)
           (com.openai.models.admin.organization.projects ProjectCreateParams)
           (java.lang.reflect Method Modifier)))
(set! *warn-on-reflection* true)

(defn- ->project-create-params ^ProjectCreateParams [{:keys [name]}]
  (when-not name (impl/missing-key! :name))
  (-> (ProjectCreateParams/builder) (.name ^String name) (.build)))

(defn- camel [k]
  (let [[head & tail] (str/split (name k) #"-")]
    (apply str head (map str/capitalize tail))))

(declare build-model)

(defn- static-method ^Method [^Class cls ^String name arity]
  (first (filter #(and (= name (.getName ^Method %))
                       (= arity (.getParameterCount ^Method %))
                       (Modifier/isStatic (.getModifiers ^Method %)))
                 (.getMethods cls))))

(defn- coerce-value [^Class target value]
  (cond
    (nil? value) nil
    (.isInstance target value) value
    (= target JsonValue) (JsonValue/from value)
    (and (.isPrimitive target) (= target Boolean/TYPE)) (boolean value)
    (and (.isPrimitive target) (= target Long/TYPE)) (long value)
    (and (.isPrimitive target) (= target Integer/TYPE)) (int value)
    (and (.isPrimitive target) (= target Double/TYPE)) (double value)
    (and (map? value) (static-method target "builder" 0)) (build-model target value)
    :else (if-let [of (static-method target "of" 1)]
            (.invoke of nil (object-array [(impl/enum-name value)]))
            value)))

(defn- invoke-setter! [builder k value]
  (let [method-name (camel k)
        methods (filter #(and (= method-name (.getName ^Method %))
                              (= 1 (.getParameterCount ^Method %)))
                        (.getMethods (class builder)))]
    (or
     (some (fn [^Method m]
             (try
               (.invoke m builder
                        (object-array [(coerce-value (aget (.getParameterTypes m) 0) value)]))
               true
               (catch Exception _ false)))
           methods)
     (when-let [^Method m (first (filter #(and (= "putAdditionalBodyProperty" (.getName ^Method %))
                                               (= 2 (.getParameterCount ^Method %)))
                                         (.getMethods (class builder))))]
       (.invoke m builder (object-array [(impl/enum-name k) (JsonValue/from value)]))
       true)
     (throw (ex-info (str "Unsupported admin parameter " k)
                     {:openai/error :unsupported-parameter :key k
                      :builder (class builder)})))))

(defn- build-model [^Class cls values]
  (let [^Method factory (static-method cls "builder" 0)
        builder (.invoke factory nil (object-array 0))]
    (doseq [[k v] values] (invoke-setter! builder k v))
    (let [^Method build (first (filter #(and (= "build" (.getName ^Method %))
                                             (zero? (.getParameterCount ^Method %)))
                                       (.getMethods (class builder))))]
      (.invoke build builder (object-array 0)))))

(defn- service-at [^OpenAIClient client path]
  (reduce (fn [target accessor]
            (let [^Method method (first (filter #(and (= (name accessor) (.getName ^Method %))
                                                       (zero? (.getParameterCount ^Method %)))
                                                 (.getMethods (class target))))]
              (.invoke method target (object-array 0))))
          client (into [:admin :organization] path)))

(defn- operation-method ^Method [service action]
  (or (first (filter #(and (= (name action) (.getName ^Method %))
                           (= 1 (.getParameterCount ^Method %))
                           (str/ends-with? (.getName ^Class (aget (.getParameterTypes ^Method %) 0)) "Params"))
                     (.getMethods (class service))))
      (first (filter #(and (= (name action) (.getName ^Method %))
                           (zero? (.getParameterCount ^Method %)))
                     (.getMethods (class service))))))

(defn request
  "Invoke an Admin API operation. `path` is the service accessor path after
  `admin().organization()`, `action` is the SDK operation, and `params` is a
  kebab-case map including path/query/body identifiers. Lists auto-page."
  [^OpenAIClient client path action params]
  (impl/with-api-errors
    (let [service (service-at client path)
          ^Method method (operation-method service action)
          param-types (.getParameterTypes method)
          result (if (zero? (alength param-types))
                   (.invoke method service (object-array 0))
                   (.invoke method service
                            (object-array [(build-model (aget param-types 0) params)])))]
      (cond
        (instance? Page result) (mapv impl/sdk-object->clj (impl/all-pages result))
        (nil? result) nil
        :else (impl/sdk-object->clj result)))))

(defn- operation-name [prefix action]
  (symbol (str prefix "-" (-> action name
                               (str/replace #"([a-z])([A-Z])" "$1-$2")
                               str/lower-case))))
(defmacro ^:private defadmin [prefix path actions]
  `(do ~@(for [action actions]
           `(defn ~(operation-name prefix action) [client# params#]
              (request client# ~path ~action params#)))))

(defadmin "admin-api-key" [:adminApiKeys] [:create :retrieve :list :delete])
(defadmin "audit-log" [:auditLogs] [:list])
(defadmin "certificate" [:certificates] [:create :retrieve :update :list :delete :activate :deactivate])
(defadmin "data-retention" [:dataRetention] [:retrieve :update])
(defadmin "group" [:groups] [:create :retrieve :update :list :delete])
(defadmin "invite" [:invites] [:create :retrieve :list :delete])
(defadmin "project" [:projects] [:create :retrieve :update :list :archive])
(defadmin "role" [:roles] [:create :retrieve :update :list :delete])
(defadmin "spend-alert" [:spendAlerts] [:create :retrieve :update :list :delete])
(defadmin "user" [:users] [:retrieve :update :list :delete])
(defadmin "group-role" [:groups :roles] [:create :retrieve :list :delete])
(defadmin "group-user" [:groups :users] [:create :retrieve :list :delete])
(defadmin "user-role" [:users :roles] [:create :retrieve :list :delete])
(defadmin "usage" [:usage]
  [:audioSpeeches :audioTranscriptions :codeInterpreterSessions :completions
   :costs :embeddings :fileSearchCalls :images :moderations :vectorStores :webSearchCalls])
