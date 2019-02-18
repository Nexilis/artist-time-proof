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

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?            true})
(def date-range (t/interval (t/date-time 2019 1 1) (t/date-time 2019 1 4)))


;; HTTP
(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))


;; Commits
(defn present-commits [])

(defn get-commits-from-single-repository [])

(defn fetch-repositories [result-promise]
  (let [url (str azure-base-url "_apis/git/repositories")]
    (client/get url
                default-http-opts
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        repo-ids     (map :id (decoded-body :value))]
                    (deliver result-promise repo-ids)))
                handle-exception)))

(defn load-commits []
  (let [repo-ids-promise (promise)]
    (fetch-repositories repo-ids-promise)
    (println "DEBUG repo-ids")
    (pp/pprint (deref repo-ids-promise))
    ;then for all repos
    (get-commits-from-single-repository)
    ;when all
    (present-commits)))

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
        creator-opts  (conj default-http-opts {:query-params {:status     "All"
                                                              :creatorId  user-id}})
        reviewer-opts (conj default-http-opts {:query-params {:status     "All"
                                                              :reviewerId user-id}})]
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

(defn fetch-user-id [result-promise]
  (let [url          (str azure-base-url "_apis/connectionData")]
    (client/get url
                default-http-opts
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        user-id (-> decoded-body :authenticatedUser :id)]
                    (deliver result-promise user-id)))
                handle-exception)))

(defn load-pull-requests []
  (let [user-id-promise (promise)
        pull-requests-channel (chan)]
    (fetch-user-id user-id-promise)
    (let [user-id (deref user-id-promise)]
      (println "DEBUG user-id" user-id)
      (fetch-pull-requests user-id pull-requests-channel)
      (present-pull-requests pull-requests-channel))))

(defn load-all []
  (load-pull-requests)
  (load-commits))


;; Start
(defn -main [& _]
  (println "DEBUG Evaluation started")
  (time (load-all))
  (println "DEBUG Evaluation ended"))