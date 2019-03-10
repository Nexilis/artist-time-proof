(ns artist-time-proof.http
  (:require
    [artist-time-proof.conf :refer :all]
    [cheshire.core :as json]
    [clj-time.core :as t]))

(def azure-base-url
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})

(def date-range (t/interval (t/minus (t/now) (t/months 1))
                            (t/now)))

(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))