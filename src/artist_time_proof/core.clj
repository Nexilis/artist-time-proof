(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clj-http.client :as http-client]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cheshire.core :as json])
  (:gen-class))

(def azure-url-base "https://dev.azure.com/%s/%s/_apis/%s%s")

(def from-date (f/unparse (f/formatters :date-time) (t/date-time 2018 1 13)))
(def to-date (f/unparse (f/formatters :date-time) (t/date-time 2019 1 13)))
(def git-author "Bartek Łukasik")

(defn- azure-url [resource query-params]
  (format azure-url-base
    (azure-conifg :organization)
    (azure-conifg :team-project)
    resource
    query-params))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories?api-version=4.1
(defn- repositories []
  (let [url (azure-url "git/repositories" "?api-version=4.1")]
    ((json/parse-string (:body (http-client/get url basic-auth)) true) :value)))

(defn- repo-ids []
  (map :id (repositories)))

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryId}/commits?searchCriteria.author={searchCriteria.author}&searchCriteria.toDate={searchCriteria.toDate}&searchCriteria.fromDate={searchCriteria.fromDate}&api-version=4.1
(defn- commit-urls []
  (let [query-params
        (str "?searchCriteria.author=" git-author
             "&searchCriteria.toDate=" to-date
             "&searchCriteria.fromDate=" from-date
             "&api-version=4.1")]
    (reduce (fn [accumulator repo-id]
                (conj accumulator
                      (let [url (azure-url (str "git/repositories/" repo-id "/commits") query-params)]
                        (println url)
                        (let [get-result (json/parse-string (:body (http-client/get url basic-auth)) true)]
                          (println get-result)
                          get-result))))
            []
            (repo-ids))))

(defn -main [& _]
  (commit-urls))