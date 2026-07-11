(ns openai.admin.projects
  "Project-scoped OpenAI Admin API wrappers."
  (:require [openai.admin :as admin]))
(set! *warn-on-reflection* true)

(defn- operation-name [prefix action]
  (symbol (str prefix "-" (-> action name
                               (clojure.string/replace #"([a-z])([A-Z])" "$1-$2")
                               clojure.string/lower-case))))
(defmacro ^:private defproject [prefix path actions]
  `(do ~@(for [action actions]
           `(defn ~(operation-name prefix action) [client# params#]
              (admin/request client# ~(into [:projects] path) ~action params#)))))

(defproject "api-key" [:apiKeys] [:retrieve :list :delete])
(defproject "certificate" [:certificates] [:list :activate :deactivate])
(defproject "data-retention" [:dataRetention] [:retrieve :update])
(defproject "group" [:groups] [:create :retrieve :list :delete])
(defproject "hosted-tool-permission" [:hostedToolPermissions] [:retrieve :update])
(defproject "model-permission" [:modelPermissions] [:retrieve :update :delete])
(defproject "rate-limit" [:rateLimits] [:listRateLimits :updateRateLimit])
(defproject "role" [:roles] [:create :retrieve :update :list :delete])
(defproject "service-account" [:serviceAccounts] [:create :retrieve :update :list :delete])
(defproject "spend-alert" [:spendAlerts] [:create :retrieve :update :list :delete])
(defproject "user" [:users] [:create :retrieve :update :list :delete])
(defproject "group-role" [:groups :roles] [:create :retrieve :list :delete])
(defproject "user-role" [:users :roles] [:create :retrieve :list :delete])
