(ns artist-time-proof.http
  (:require
    [cheshire.core :as json]
    [clj-time.format :as f]
    [taoensso.timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(defn date->query-string [date]
  (f/unparse
    (f/formatters :date-time) date))

(defn handle-exception [exception]
  (error exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))