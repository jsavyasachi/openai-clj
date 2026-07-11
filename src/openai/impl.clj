(ns openai.impl
  "Shared implementation helpers for OpenAI SDK wrapper namespaces."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jsonista.core :as json])
  (:import (com.openai.core AutoPager JsonValue Page)
           (com.openai.errors BadRequestException
                              InternalServerException
                              NotFoundException
                              OpenAIIoException
                              OpenAIServiceException
                              PermissionDeniedException
                              RateLimitException
                              UnauthorizedException
                              UnexpectedStatusCodeException
                              UnprocessableEntityException)))

(set! *warn-on-reflection* true)

(defn service-error-type [e]
  (condp instance? e
    BadRequestException :bad-request
    UnauthorizedException :unauthorized
    PermissionDeniedException :permission-denied
    NotFoundException :not-found
    UnprocessableEntityException :unprocessable-entity
    RateLimitException :rate-limit
    InternalServerException :internal-server
    UnexpectedStatusCodeException :unexpected-status
    :api-error))

(defn throw-normalized!
  "Rethrow an SDK exception: service errors and I/O errors become ex-info
  keyed `:openai/error` with the original as cause; anything else propagates
  unchanged."
  [^Throwable e]
  (cond
    (instance? OpenAIServiceException e)
    (throw (ex-info (or (.getMessage e) "OpenAI API error")
                    {:openai/error :api-error
                     :status (.statusCode ^OpenAIServiceException e)
                     :error-type (service-error-type e)}
                    e))
    (instance? OpenAIIoException e)
    (throw (ex-info (or (.getMessage e) "OpenAI I/O error")
                    {:openai/error :io-error}
                    e))
    :else (throw e)))

(defmacro with-api-errors [& body]
  `(try ~@body
        (catch com.openai.errors.OpenAIException e#
          (openai.impl/throw-normalized! e#))))

(defn missing-key! [k]
  (throw (ex-info (str "Missing required key " k)
                  {:openai/error :missing-key :key k})))

(defn enum-name [k]
  (str/replace (name k) "-" "_"))

(defn ->keyword [s]
  (-> s str str/lower-case (str/replace "_" "-") keyword))

(defn opt-get [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(def json-mapper (json/object-mapper {:decode-key-fn true}))

(defn parse-arguments [^String s]
  (try
    (json/read-value s json-mapper)
    (catch Exception _
      s)))

(defn encode-output [x]
  (if (string? x)
    x
    (json/write-value-as-string x)))

(defn ->json-value-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn ->json-schema-properties [m]
  (into {}
        (map (fn [[k v]] [k (JsonValue/from v)]))
        (walk/stringify-keys m)))

(defn all-pages
  "Realize every element across all pages of an SDK *ListPage via its autoPager."
  [page]
  (vec (AutoPager. ^Page page nil)))
