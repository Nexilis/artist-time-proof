(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-http.client :as client]
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-pdf.core :as pdf]
    [clojure.pprint :as pp])
  (:gen-class))

(def pull-requests-chan (chan))
(def commits-chan (chan))

;; Configurations
(def azure-base-url
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})
(def date-range (t/interval (t/date-time 2019 1 1) (t/date-time 2019 1 4)))


;; HTTP
(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))


;; Commits
(defn fetch-commits [repo-ids]
  (doseq [repo-id repo-ids]
    (let [url (str azure-base-url "_apis/git/repositories/" repo-id "/commits")
          opts default-http-opts]
      (client/get url
                  opts                                      ;; TODO: filter with dates and author
                  (fn [response] (go (>! commits-chan (extract-value-from response))))
                  handle-exception))))


(defn fetch-repositories [result-promise]
  (let [url (str azure-base-url "_apis/git/repositories")]
    (client/get url
                default-http-opts                           ;; TODO: includeHidden: true, includeLinks: false, consider handling paging
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        repo-ids (map :id (decoded-body :value))]
                    (deliver result-promise repo-ids)))
                handle-exception)))

(defn load-commits []
  (let [repo-ids-promise (promise)]
    (fetch-repositories repo-ids-promise)
    (println "DEBUG repo-ids")
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits repo-ids))))

;; Pull Requests
(defn completed? [pr]
  (= (:status pr) "completed"))

(defn in-date-range? [date-time-string]
  (t/within? date-range (f/parse date-time-string)))

(defn filter-pull-requests [pr]
  (if (completed? pr)
    (in-date-range? (:closedDate pr))
    (in-date-range? (:creationDate pr))))

(defn fetch-pull-requests [user-id]
  (let [url (str azure-base-url "_apis/git/pullrequests")
        creator-opts (conj default-http-opts {:query-params {:status    "All"
                                                             :creatorId user-id}})
        reviewer-opts (conj default-http-opts {:query-params {:status     "All"
                                                              :reviewerId user-id}})]
    (client/get url
                creator-opts
                (fn [response]
                  (go (>! pull-requests-chan (filter filter-pull-requests
                                                     (extract-value-from response)))))
                handle-exception)
    (client/get url
                reviewer-opts
                (fn [response]
                  (go (>! pull-requests-chan (filter filter-pull-requests
                                                     (extract-value-from response)))))
                handle-exception)))

(defn fetch-user-id [result-promise]
  (let [url (str azure-base-url "_apis/connectionData")]
    (client/get url
                default-http-opts
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        user-id (-> decoded-body :authenticatedUser :id)]
                    (deliver result-promise user-id)))
                handle-exception)))

(defn load-pull-requests []
  (let [user-id-promise (promise)]
    (fetch-user-id user-id-promise)
    (let [user-id (deref user-id-promise)]
      (println "DEBUG user-id" user-id)
      (fetch-pull-requests user-id))))

;; Main
(defn present-results [present-completion]
  (<!! (go
         (pdf/pdf
           [{:title "Artist Time Proof"
             :size "a4"
             :footer "page"
             :left-margin   15
             :right-margin  15
             :top-margin    20
             :bottom-margin 20}
            [:chapter "TEST"]
            [:paragraph "test"]
            [:chapter "PULL REQUESTS"]
            (dotimes [_ 2]
              [:paragraph (str (<! pull-requests-chan))])
            (close! pull-requests-chan)
            [:chapter "COMMITS"]
            (dotimes [_ 50]                                    ;; assuming that there are no more than 50 repositories, TODO: replace with smth smarter
              [:paragraph (str (<! commits-chan))])
            (close! commits-chan)]
           (str "artist-time-proof" (f/unparse (f/formatters :date-time) (t/now)) ".pdf"))
         (deliver present-completion true))))

(defn load-all []
  (go
    (load-pull-requests))
  (go
    (load-commits))
  (let [present-completion (promise)]
    (present-results present-completion)
    (if (deref present-completion) (println "DEBUG Document generation done"))))

(defn -main [& _]
  (println "DEBUG Program start")
  (time (load-all))
  (println "DEBUG Program end"))