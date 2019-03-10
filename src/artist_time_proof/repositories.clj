(ns artist-time-proof.repositories
  (:require
    [artist-time-proof.http :refer :all]
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def url-repositories (str azure-base-url "_apis/git/repositories"))

(defn fetch-repositories [result-promise]
  (http/get url-repositories
            ;; TODO: includeHidden: true, includeLinks: false, consider handling paging
            default-http-opts
            (fn [response]
              (let [decoded-body (json/parse-string (:body response) true)
                    repo-ids (map :id (decoded-body :value))]
                (deliver result-promise repo-ids)))
            handle-exception))