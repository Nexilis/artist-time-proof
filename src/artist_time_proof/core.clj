(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-http.client :as client]
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


;; HTTP
(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))


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
(defn present-pull-requests [responses]
  (<!! (go
         (let [creator-resp (<! responses)
               reviewer-resp (<! responses)]
           (println "DEBUG creator-resp")
           (pp/pprint creator-resp)
           (println "DEBUG reviewer-resp")
           (pp/pprint reviewer-resp))))
  (close! responses))

(defn completed? [pr]
  (= (:status pr) "completed"))

(defn in-date-range? [date-time-string]
  (t/within? date-range (f/parse date-time-string)))

(defn filter-pull-requests [pr]
  (if (completed? pr)
    (in-date-range? (:closedDate pr))
    (in-date-range? (:creationDate pr))))

(defn fetch-pull-requests [user-id resp-channel]
  (let [url (str azure-base-url "_apis/git/pullrequests")
        creator-opts (conj basic-auth {:query-params {:status    "All"
                                                      :creatorId user-id}}
                           {:async? true})

        reviewer-opts (conj basic-auth {:query-params {:status     "All"
                                                       :reviewerId user-id}}
                            {:async? true})]
    (client/get url
                creator-opts
                (fn [response]
                  (go (>! resp-channel (filter filter-pull-requests
                                               (extract-value-from response)))))
                handle-exception)
    (client/get url
                reviewer-opts
                (fn [response]
                  (go (>! resp-channel (filter filter-pull-requests
                                               (extract-value-from response)))))
                handle-exception)))

(defn fetch-user-id []
  (let [url (str azure-base-url "_apis/connectionData")
        response (:body (client/get url basic-auth))
        decoded-body (json/parse-string response true)
        user-id (-> decoded-body :authenticatedUser :id)]
    user-id))

(defn load-pull-requests []
  (let [user-id (fetch-user-id)
        pull-requests-channel (chan)]
    (println "DEBUG user-id" user-id)
    (fetch-pull-requests user-id pull-requests-channel)
    (present-pull-requests pull-requests-channel)))

(defn load-all []
  (load-pull-requests)
  (load-commits))


;; Start
(defn -main [& _]
  (println "DEBUG Evaluation started")
  (time (load-all))
  (println "DEBUG Evaluation ended"))