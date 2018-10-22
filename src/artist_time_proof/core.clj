(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clj-http.client :as http-client]
    [cheshire.core :as json])
  (:gen-class))

(defn- azure-url [area resource version]
  (format "https://dev.azure.com/%s/%s/_apis/%s/%s?api-version=%s"
    (azure-conifg :organization)
    (azure-conifg :team-project)
    area
    resource
    version))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})

(defn- repositories []
  (let [url (azure-url "git" "repositories" "4.1")]
    ((json/parse-string (:body (http-client/get url basic-auth)) true) :value)))

(defn -main [& args]
  (repositories))

;; TOOD: continue here
;; (filter #(:id %) (repositories))
