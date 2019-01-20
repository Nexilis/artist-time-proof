(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [org.httpkit.client :as http]
    [cheshire.core :as json])
  (:gen-class))

;; Configurations

(def azure-base-url
  (format "https://dev.azure.com/%s/"
    (azure-config :organization)))

(def basic-auth {:basic-auth [(auth :user) (auth :pass)]})


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

(defn get-pull-requests [user-id]
  (let [url (str azure-base-url "_apis/git/pullrequests")
        creator-opts (conj basic-auth {:query-params {:status    "All"
                                                      :creatorId user-id}})
        reviewer-opts (conj basic-auth {:query-params {:status     "All",
                                                       :reviewerId user-id}})]

    (let [futures (doall (map http/get [url url]
                                       [creator-opts reviewer-opts]))]
      (doseq [resp futures]
        ;; wait for server response synchronously
        (println (-> @resp :opts :url) " status: " (:status @resp))))))
        ;; filter via dates and return list

(defn get-user-id []
 (let [url (str azure-base-url "_apis/connectionData")
       promise-of-user-id (promise)]
   (http/get url basic-auth
             (fn [{:keys [status headers body error]}]
               (let [decoded-body (json/parse-string body true)]
                 (deliver promise-of-user-id (:id (:authenticatedUser decoded-body))))))
   @promise-of-user-id))

(defn load-pull-requests []
  (let [user-id (get-user-id)]
    (println user-id)
    ;then
    (get-pull-requests user-id)
    ;when all
    (present-pull-requests)))

(defn load-all []
  (load-pull-requests)
  (load-commits))


;; Start

(defn -main [& _]
  (println "Evaluation started...")
  (load-all)
  (println "...evaluation ended"))
  ;(time (Thread/sleep 2000)))