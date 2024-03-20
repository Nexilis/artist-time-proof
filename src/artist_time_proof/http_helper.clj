(ns artist-time-proof.http-helper
  (:require
   [cheshire.core :as json]
   [clj-time.format :as f]))

(defn date->query-string [date]
  (f/unparse
   (f/formatters :date-time) date))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))
