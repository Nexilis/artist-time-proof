(ns artist-time-proof.pull-requests
  (:require
    [artist-time-proof.http :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [cheshire.core :as json]
    [clj-http.client :as http]
    [clj-time.format :as f]
    [clj-time.core :as t]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(def pull-requests-chan (chan 2))
(def pull-requests-response-count (atom 0))

(defn- completed? [pr]
  (= (:status pr) "completed"))

(defn- in-date-range? [date-time-string]
  (t/within? last-month-range (f/parse date-time-string)))

(defn- filter-pull-requests [pr]
  (if (completed? pr)
    (in-date-range? (:closedDate pr))
    (in-date-range? (:creationDate pr))))

(defn- close-pull-requests-chan! [callback-no]
  (close! pull-requests-chan)
  (debug "closed pull-requests-chan in callback no" callback-no))

(defn- put-on-pull-requests-chan! [response callback-no]
  (put! pull-requests-chan
        (filter filter-pull-requests
                (extract-value-from response)))
  (debug "put on pull-requests-chan in callback no" callback-no))

(defn- handle-prs-fetch-success! [response]
  (swap! pull-requests-response-count inc)
  (let [callback-no (deref pull-requests-response-count)]
    (debug "pull request callback no" callback-no)
    (put-on-pull-requests-chan! response callback-no)
    (if (= callback-no 2)
      (close-pull-requests-chan! callback-no))))

(defn- single-pull-request-fetch [options]
  (http/get url-pull-requests
            options
            handle-prs-fetch-success!
            handle-exception))

(defn- fetch-pull-requests [user-id]
  (let [options [(conj default-http-opts {:query-params {:status "All" :creatorId user-id}})
                 (conj default-http-opts {:query-params {:status "All" :reviewerId user-id}})]]
    (doall (map single-pull-request-fetch options))))

(defn- fetch-user-id [result-promise]
  (http/get url-user-id
            default-http-opts
            (fn [response]
              (let [decoded-body (json/parse-string (:body response) true)
                    user-id (-> decoded-body :authenticatedUser :id)]
                (deliver result-promise user-id)))
            handle-exception))

(defn load-pull-requests []
  (let [user-id-promise (promise)]
    (fetch-user-id user-id-promise)
    (let [user-id (deref user-id-promise)]
      (info "user-id" user-id)
      (fetch-pull-requests user-id))))