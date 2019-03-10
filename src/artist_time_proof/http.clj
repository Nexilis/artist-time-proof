(ns artist-time-proof.http
  (:require
    [artist-time-proof.conf :refer :all]
    [cheshire.core :as json]
    [clj-time.core :as t]))

(def url-dev-azure-org
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def url-user-id (str url-dev-azure-org "_apis/connectionData"))
(def url-repositories (str url-dev-azure-org "_apis/git/repositories"))
(def url-pull-requests (str url-dev-azure-org "_apis/git/pullrequests"))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})

(def date-range (t/interval (t/minus (t/now) (t/months 1))
                            (t/now)))

(defn url-commits [repo-id]
  (str url-dev-azure-org "_apis/git/repositories/" repo-id "/commits"))

(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))