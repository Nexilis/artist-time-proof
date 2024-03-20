(ns artist-time-proof.commits
  (:require
   [clojure.core.async :as async :exclude [map into reduce merge take transduce partition partition-by]]
   [clojure.string :as string]
   [artist-time-proof.http-helper :as http-helper]
   [artist-time-proof.repositories :as repos]
   [clj-http.client :as http]
   [taoensso.timbre :as timbre]))

(def commits-chan (async/chan))
(def commits-response-count (atom 0))

(defn- handle-success! [repos-count response url]
  (swap! commits-response-count inc)
  (let [callback-no (deref commits-response-count)]
    (cond
      (= (:status response) 200)
      (async/put! commits-chan (http-helper/extract-value-from response))

      (= (:status response) 404)
      (timbre/info (str "No resource found for url: " url))

      :else
      (timbre/error response))
    (if (= callback-no repos-count)
      (async/close! commits-chan))))

(defn- fetch-commits [app-config repo-ids]
  (doseq [repo-id repo-ids]
    (let [repos-count (count repo-ids)
          url (string/replace (-> app-config :url :commits) #"repo-id" repo-id)
          request-options (-> (:request-options app-config)
                              (assoc :query-params {:author   (:author app-config)
                                                    :fromDate (http-helper/date->query-string (:date-from app-config))
                                                    :toDate   (http-helper/date->query-string (:date-to app-config))}))]
      (http/get url
                request-options
                (fn [response] (handle-success! repos-count response url))
                (fn [exception] (timbre/error exception))))))

(defn fetch [app-config]
  (let [repo-ids-promise (promise)]
    (repos/fetch app-config repo-ids-promise)
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits app-config repo-ids))))
