(ns artist-time-proof.http-helper
  (:require
   [cheshire.core :as json]
   [clj-time.format :as f]
   [taoensso.timbre :as timbre]))

(defn date->query-string [date]
  (f/unparse
   (f/formatters :date-time) date))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))

(defn handle-exception [exception]
  (timbre/error exception))
