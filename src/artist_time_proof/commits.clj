(ns artist-time-proof.commits
  (:require
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clojure.string :as string]
    [artist-time-proof.http :refer :all]
    [artist-time-proof.repositories :refer :all]
    [clj-http.client :as http]
    [cheshire.core :as json]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))


(def commits-chan (chan))
(def commits-response-count (atom 0))

(defn- close-commits-chan! [callback-no]
  (close! commits-chan)
  (debug "closed commits-chan in callback no" callback-no))

(defn- put-on-commits-chan! [response callback-no]
  (put! commits-chan (extract-value-from response))
  (debug "put on commits-chan in callback no" callback-no))

(defn- handle-commits-fetch-success! [response repos-count]
  (swap! commits-response-count inc)
  (let [callback-no (deref commits-response-count)]
    (debug "commits callback no" callback-no)
    (put-on-commits-chan! response callback-no)
    (if (= callback-no repos-count)
      (close-commits-chan! callback-no))))

(defn- fetch-commits [app-config repo-ids]
  (doseq [repo-id repo-ids]
    (let [repos-count (count repo-ids)]
      (http/get (string/replace (-> app-config :url :commits) #"repo-id" repo-id)
                (conj (:request-options app-config) {:query-params {:author   (:author app-config)
                                                                    :fromDate (date->query-string (:date-from app-config))
                                                                    :toDate   (date->query-string (:date-to app-config))}})
                (fn [response] (handle-commits-fetch-success! response
                                                              repos-count))
                handle-exception))))

(defn load-commits [app-config]
  (let [repo-ids-promise (promise)]
    (fetch-repositories app-config repo-ids-promise)
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits app-config repo-ids))))