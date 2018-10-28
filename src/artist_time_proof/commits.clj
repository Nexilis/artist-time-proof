(ns artist-time-proof.commits
  (:require
    [artist-time-proof.http :refer :all]
    [artist-time-proof.repositories :refer :all]
    [clj-time.core :as t]
    [clj-time.format :as f]))

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryId}/commits?searchCriteria.author={searchCriteria.author}&searchCriteria.toDate={searchCriteria.toDate}&searchCriteria.fromDate={searchCriteria.fromDate}&api-version=4.1

(def from-date (f/unparse (f/formatters :date-time) (t/date-time 2018 10 01)))
(def to-date (f/unparse (f/formatters :date-time) (t/date-time 2018 11 01)))
(def git-author "Bartek Łukasik")

(defn- get-commits-query-params []
  (str "?searchCriteria.author=" git-author
       "&searchCriteria.toDate=" to-date
       "&searchCriteria.fromDate=" from-date
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