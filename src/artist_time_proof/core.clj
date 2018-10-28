(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clj-http.client :as http-client]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [cheshire.core :as json])
  (:gen-class))

(def azure-url-base "https://dev.azure.com/%s/%s/_apis/%s%s")

(def from-date (f/unparse (f/formatters :date-time) (t/date-time 2018 10 01)))
(def to-date (f/unparse (f/formatters :date-time) (t/date-time 2018 11 01)))
(def git-author "Bartek Łukasik")

(defn- azure-url [resource query-params]
  (format azure-url-base
    (azure-conifg :organization)
    (azure-conifg :team-project)
    resource
    query-params))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})

(defn- http-get-response-body [url]
  (json/parse-string (:body (http-client/get url basic-auth)) true))

(defn- repositories []
  (let [url (azure-url "git/repositories" "?api-version=4.1")]
    ((http-get-response-body url) :value)))

(defn- repo-ids []
  (map :id (repositories)))


(def get-commits-query-params
  (str "?searchCriteria.author=" git-author
       "&searchCriteria.toDate=" to-date
       "&searchCriteria.fromDate=" from-date
       "&api-version=4.1"))

(defn- get-commits-url [repo-id]
  (azure-url (str "git/repositories/" repo-id "/commits") get-commits-query-params))

(defn- commits []
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

(defn -main [& _]
  (println (commits)))