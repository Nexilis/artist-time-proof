(ns artist-time-proof.commits
  (:require
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
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

(defn- fetch-commits [repo-ids]
  (doseq [repo-id repo-ids]
    (let [repos-count (count repo-ids)]
      (http/get (url-commits repo-id)
                default-http-opts                           ;; TODO: filter with dates and author
                (fn [response] (handle-commits-fetch-success! response
                                                              repos-count))
                handle-exception))))

(defn load-commits []
  (let [repo-ids-promise (promise)]
    (fetch-repositories repo-ids-promise)
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits repo-ids))))