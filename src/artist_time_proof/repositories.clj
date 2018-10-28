(ns artist-time-proof.repositories
  (:require
    [artist-time-proof.http :refer :all]))

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories?api-version=4.1

(defn- repositories []
  (let [url (azure-url "git/repositories" "?api-version=4.1")]
    ((http-get-response-body url) :value)))

(defn repo-ids []
  (map :id (repositories)))