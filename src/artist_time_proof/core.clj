(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [org.httpkit.client :as http]
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.pprint :as pp])
  (:gen-class))

;; Configurations

(def azure-base-url
  (format "https://dev.azure.com/%s/"
    (azure-config :organization)))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})
(def date-range (t/interval (t/date-time 2019 1 1) (t/date-time 2019 1 4)))

;; Commits
(defn present-commits [])

(defn get-commits-from-single-repository [])

(defn get-repositories [])

(defn load-commits []
 (get-repositories)
 ;then for all repos
 (get-commits-from-single-repository)
 ;when all
 (present-commits))


;; Pull Requests
(defn present-pull-requests [])

(defn completed? [pr]
  (= (:status pr) "completed"))

(defn in-date-range? [date-time-string]
  (t/within? date-range (f/parse date-time-string)))

(defn filter-pull-requests [pr]
  (if (completed? pr)
    (in-date-range? (:closedDate pr))
    (in-date-range? (:creationDate pr))))

(defn get-pull-requests [user-id]
  (let [url (str azure-base-url "_apis/git/pullrequests")
        creator-opts (conj basic-auth {:query-params {:status    "All"
                                                      :creatorId user-id}})
        reviewer-opts (conj basic-auth {:query-params {:status     "All"
                                                       :reviewerId user-id}})]
    ;; send the request concurrently (asynchronously)
    (let [futures (doall (map http/get [url url]
                                       [creator-opts reviewer-opts]))]
      (doseq [resp futures]
        ;; wait for server response synchronously
        (println "DEBUG Request" (-> @resp :opts :url) "ended with status:" (:status @resp))
        (let [pull-requests ((json/parse-string (-> @resp :body) true) :value)]
          (pp/pprint
            (filter filter-pull-requests pull-requests)))))))

(defn get-user-id []
 (let [url (str azure-base-url "_apis/connectionData")
       promise-of-user-id (promise)]
   (http/get url basic-auth
             (fn [{:keys [status headers body error]}]
               (let [decoded-body (json/parse-string body true)]
                 (deliver promise-of-user-id (-> decoded-body :authenticatedUser :id)))))
   @promise-of-user-id))

(defn load-pull-requests []
  (let [user-id (get-user-id)]
    (println "DEBUG user-id" user-id)
    ;then
    (get-pull-requests user-id)
    ;when all
    (present-pull-requests)))

(defn load-all []
  (load-pull-requests)
  (load-commits))


;; Start

(defn -main [& _]
  (println "DEBUG Evaluation started")
  (load-all)
  (println "DEBUG Evaluation ended"))