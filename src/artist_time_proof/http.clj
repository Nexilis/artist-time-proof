(ns artist-time-proof.http
  (:require
    [artist-time-proof.conf :refer :all]
    [clj-http.client :as http-client]
    [cheshire.core :as json]))

(def azure-url-base "https://dev.azure.com/%s/%s/_apis/%s%s")

(defn azure-url [resource query-params]
  (format azure-url-base
          (azure-config :organization)
          (azure-config :team-project)
          resource
          query-params))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})

(defn http-get-response-body [url]
  (json/parse-string (:body (http-client/get url basic-auth)) true))