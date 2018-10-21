(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clj-http.client :as client]
    [clojure.tools.cli :as cli])
  (:gen-class))

(defn- azure-url [area resource version]
  (format "https://dev.azure.com/%s/%s/_apis/%s/%s?api-version=%s"
    (azure-conifg :organization)
    (azure-conifg :team-project)
    area
    resource
    version))

(defn -main [& args]
  (let [list-repositories-url (azure-url "git" "repositories" "4.1")
        basic-auth {:basic-auth [(auth :user) (auth :pass)]}]
    (client/get list-repositories-url (merge basic-auth {:as :json}))))
