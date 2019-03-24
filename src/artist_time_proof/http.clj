(ns artist-time-proof.http
  (:require
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [taoensso.timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(defn date->query-string [date]
  (f/unparse
    (f/formatters :date-time) date))

(defn build-http-config [options]
  (let [url-base (format "https://dev.azure.com/%s/" (:org options))]
    {:author          (:user options)
     :url             {:connection-data (str url-base "_apis/connectionData")
                       :repositories    (str url-base "_apis/git/repositories")
                       :pull-requests   (str url-base "_apis/git/pullrequests")
                       :commits         (str url-base "_apis/git/repositories/repo-id/commits")}
     :request-options {:basic-auth [(:user options) (:pass options)]
                       :async?     true}
     :date-from       (:date-from options)
     :date-to         (:date-to options)}))

(def today (t/now))

(def month-ago (t/minus today (t/months 1)))

(defn handle-exception [exception]
  (error exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))