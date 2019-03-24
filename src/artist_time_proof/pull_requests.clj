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

(defn- in-date-range? [date-time-string date-range]
  (t/within? date-range (f/parse date-time-string)))

(defn- filter-pull-requests [response date-range]
  (let [extracted-response-value (extract-value-from response)]
    (filter #(if (completed? %)
               (in-date-range? (:closedDate %) date-range)
               (in-date-range? (:creationDate %) date-range))
            extracted-response-value)))

(defn- close-pull-requests-chan! [callback-no]
  (close! pull-requests-chan)
  (debug "closed pull-requests-chan in callback no" callback-no))

(defn- put-on-pull-requests-chan! [filtered-pr callback-no]
  (put! pull-requests-chan filtered-pr)
  (debug "put on pull-requests-chan in callback no" callback-no))

(defn- handle-prs-fetch-success! [response http-config]
  (swap! pull-requests-response-count inc)
  (let [callback-no (deref pull-requests-response-count)
        last-month-range (t/interval (:date-from http-config) (:date-to http-config))
        filtered-pull-request (filter-pull-requests response last-month-range)]
    (debug "pull request callback no" callback-no)
    (put-on-pull-requests-chan! filtered-pull-request callback-no)
    (if (= callback-no 2)
      (close-pull-requests-chan! callback-no))))

(defn- single-pull-request-fetch [http-config]
  (debug http-config)
  (http/get (:pull-requests-url http-config)
            (:request-options http-config)
            (fn [response] (handle-prs-fetch-success! response http-config))
            handle-exception))

(defn- build-pull-request-config [http-config query-params]
  {:pull-requests-url (-> http-config :url :pull-requests)
   :request-options   (conj (:request-options http-config) query-params)
   :date-from         (:date-from http-config)
   :date-to           (:date-to http-config)})

(defn- fetch-pull-requests [http-config user-id]
  (let [http-configs [(build-pull-request-config http-config {:query-params {:status "All" :creatorId user-id}})
                      (build-pull-request-config http-config {:query-params {:status "All" :reviewerId user-id}})]]
    (doall (map single-pull-request-fetch http-configs))))

(defn- fetch-user-id [http-config result-promise]
  (http/get (-> http-config :url :connection-data)
            (:request-options http-config)
            (fn [response]
              (let [decoded-body (json/parse-string (:body response) true)
                    user-id (-> decoded-body :authenticatedUser :id)]
                (deliver result-promise user-id)))
            handle-exception))

(defn load-pull-requests [http-config]
  (let [user-id-promise (promise)]
    (fetch-user-id http-config user-id-promise)
    (let [user-id (deref user-id-promise)]
      (info "user-id" user-id)
      (fetch-pull-requests http-config user-id))))