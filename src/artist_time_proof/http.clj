(ns artist-time-proof.http
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [taoensso.timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(defn build-http-config [azure-options]
  (let [url-base (format "https://dev.azure.com/%s/" (:org azure-options))]
    {:author          (:user azure-options)
     :url             {:connection-data (str url-base "_apis/connectionData")
                       :repositories    (str url-base "_apis/git/repositories")
                       :pull-requests   (str url-base "_apis/git/pullrequests")
                       :commits         (str url-base "_apis/git/repositories/repo-id/commits")}
     :request-options {:basic-auth [(:user azure-options) (:pass azure-options)]
                       :async?     true}}))

(def today (t/now))

(def month-ago (t/minus today (t/months 1)))

(def last-month-range (t/interval month-ago today))

(defn date->query-string [date]
  (f/unparse
    (f/formatters :date-time) date))

(defn handle-exception [exception]
  (error exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))