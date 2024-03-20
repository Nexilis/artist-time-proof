(ns artist-time-proof.pull-requests
  (:require
   [artist-time-proof.http-helper :as http-helper]
   [clojure.core.async :as async :exclude [map into reduce merge take transduce partition partition-by]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clj-time.format :as f]
   [clj-time.core :as t]))

(def pull-requests-chan (async/chan 2))
(def pull-requests-response-count (atom 0))

(defn- completed? [pr]
  (= (:status pr) "completed"))

(defn- in-date-range? [date-time-string date-range]
  (t/within? date-range (f/parse date-time-string)))

(defn- filter-pull-requests [response date-range]
  (let [response-value (http-helper/extract-value-from response)]
    (filter #(if (completed? %)
               (in-date-range? (:closedDate %) date-range)
               (in-date-range? (:creationDate %) date-range))
            response-value)))

(defn- handle-prs-fetch-success! [response pr-config]
  (swap! pull-requests-response-count inc)
  (let [callback-no (deref pull-requests-response-count)
        last-month-range (t/interval (:date-from pr-config) (:date-to pr-config))
        filtered-pull-request (filter-pull-requests response last-month-range)]
    (async/put! pull-requests-chan filtered-pull-request)
    (if (= callback-no 2)
      (async/close! pull-requests-chan))))

(defn- single-pull-request-fetch [pr-config]
  (http/get (:pull-requests-url pr-config)
            (:request-options pr-config)
            (fn [response] (handle-prs-fetch-success! response pr-config))
            http-helper/handle-exception))

(defn- pull-request-config [app-config query-params]
  {:pull-requests-url (-> app-config :url :pull-requests)
   :request-options   (conj (:request-options app-config) query-params)
   :date-from         (:date-from app-config)
   :date-to           (:date-to app-config)})

(defn- fetch-pull-requests [app-config user-id]
  (let [pr-configs [(pull-request-config app-config {:query-params {:status "All" :creatorId user-id}})
                    (pull-request-config app-config {:query-params {:status "All" :reviewerId user-id}})]]
    (doall (map single-pull-request-fetch pr-configs))))

(defn- fetch-user-id [app-config result-promise]
  (http/get (-> app-config :url :connection-data)
            (:request-options app-config)
            (fn [response]
              (let [decoded-body (json/parse-string (:body response) true)
                    user-id (-> decoded-body :authenticatedUser :id)]
                (deliver result-promise user-id)))
            http-helper/handle-exception))

(defn fetch [app-config]
  (let [user-id-promise (promise)]
    (fetch-user-id app-config user-id-promise)
    (let [user-id (deref user-id-promise)]
      (fetch-pull-requests app-config user-id))))
