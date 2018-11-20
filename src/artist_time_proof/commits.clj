(ns artist-time-proof.commits
  (:require
    [artist-time-proof.conf :refer :all]
    [artist-time-proof.http :refer :all]
    [artist-time-proof.repositories :refer :all]))

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryId}/commits?searchCriteria.author={searchCriteria.author}&searchCriteria.toDate={searchCriteria.toDate}&searchCriteria.fromDate={searchCriteria.fromDate}&api-version=4.1

(defn- get-commits-query-params []
  (str "?searchCriteria.author="   (azure-config :git-author)
       "&searchCriteria.toDate="   (azure-config :to-date)
       "&searchCriteria.fromDate=" (azure-config :from-date)
       "&api-version=4.1"))

(defn- get-commits-url [repo-id]
  (azure-url (str "git/repositories/" repo-id "/commits") (get-commits-query-params)))

(defn commits []
  (let [commits-from-all-repos
        (flatten
          (reduce (fn [accumulator repo-id]
                    (conj accumulator
                          (let [commits-from-single-repo (http-get-response-body (get-commits-url repo-id))]
                            (if (> (commits-from-single-repo :count) 0)
                                (commits-from-single-repo :value)
                                []))))
                  []
                  (repo-ids)))]
    (map :url commits-from-all-repos)))