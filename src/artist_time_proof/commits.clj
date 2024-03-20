(ns artist-time-proof.commits
  (:require
   [clojure.core.async :as async :exclude [map into reduce merge take transduce partition partition-by]]
   [clojure.string :as string]
   [artist-time-proof.http-helper :as http-helper]
   [artist-time-proof.repositories :as repos]
   [clj-http.client :as http]))

(def commits-chan (async/chan))
(def commits-response-count (atom 0))

(defn- handle-commits-fetch-success! [response repos-count]
  (swap! commits-response-count inc)
  (let [callback-no (deref commits-response-count)]
    (async/put! commits-chan (http-helper/extract-value-from response))
    (if (= callback-no repos-count)
      (async/close! commits-chan))))

(defn- fetch-commits [app-config repo-ids]
  (doseq [repo-id repo-ids]
    (let [repos-count (count repo-ids)]
      (http/get (string/replace (-> app-config :url :commits) #"repo-id" repo-id)
                (conj (:request-options app-config) {:query-params {:author   (:author app-config)
                                                                    :fromDate (http-helper/date->query-string (:date-from app-config))
                                                                    :toDate   (http-helper/date->query-string (:date-to app-config))}})
                (fn [response] (handle-commits-fetch-success! response
                                                              repos-count))
                http-helper/handle-exception))))

(defn fetch [app-config]
  (let [repo-ids-promise (promise)]
    (repos/fetch app-config repo-ids-promise)
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits app-config repo-ids))))
