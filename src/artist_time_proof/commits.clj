(ns artist-time-proof.commits
  (:require
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [artist-time-proof.http :refer :all]
    [artist-time-proof.repositories :refer :all]
    [clj-http.client :as http]
    [cheshire.core :as json]))

(def commits-chan (chan))
(def commits-response-count (atom 0))

(defn- close-commits-chan! [callback-no]
  (close! commits-chan)
  (println "DEBUG closed commits-chan in callback no" callback-no))

(defn- put-on-commits-chan! [response callback-no]
  (put! commits-chan (extract-value-from response))
  (println "DEBUG put on commits-chan in callback no" callback-no))

(defn- handle-commits-fetch-success! [response repos-count]
  (swap! commits-response-count inc)
  (let [callback-no (deref commits-response-count)]
    (println "DEBUG commits callback no" callback-no)
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
    (println "DEBUG repo-ids")
    (let [repo-ids (deref repo-ids-promise)]
      (println "DEBUG repos count" (count repo-ids))
      (fetch-commits repo-ids))))