(ns artist-time-proof.repositories
  (:require
    [artist-time-proof.http :refer :all]
    [cheshire.core :as json]
    [clj-http.client :as http]
    [taoensso.timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(defn- handle-repositories-fetch-success! [response result-promise]
  (let [decoded-body (json/parse-string (:body response) true)
        repo-ids (map :id (decoded-body :value))]
    (info "repos count" (count repo-ids))
    (deliver result-promise repo-ids)))

(defn fetch-repositories [app-config result-promise]
  (http/get (-> app-config :url :repositories)
            (conj (:request-options app-config) {:query-params  {:includeLinks false}
                                                 :includeHidden true})
            (fn [response] (handle-repositories-fetch-success! response result-promise))
            handle-exception))