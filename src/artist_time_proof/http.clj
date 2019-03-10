(ns artist-time-proof.http
  (:require
    [artist-time-proof.conf :refer :all]
    [cheshire.core :as json]
    [clj-time.core :as t]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(def url-dev-azure-org
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def url-user-id (str url-dev-azure-org "_apis/connectionData"))
(def url-repositories (str url-dev-azure-org "_apis/git/repositories"))
(def url-pull-requests (str url-dev-azure-org "_apis/git/pullrequests"))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})

(def month-ago (t/minus (t/now)
                        (t/months 1)))

(def date-range (t/interval month-ago (t/now)))

(defn url-commits [repo-id]
  (str url-dev-azure-org "_apis/git/repositories/" repo-id "/commits"))

(defn handle-exception [exception]
  (error exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))