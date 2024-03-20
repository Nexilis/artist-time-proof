(ns artist-time-proof.repositories
  (:require
   [artist-time-proof.http-helper :as http-helper]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [taoensso.timbre :as timbre]))

(defn- handle-fetch-success! [response result-promise]
  (let [decoded-body (json/parse-string (:body response) true)
        repo-ids (map :id (decoded-body :value))]
    (timbre/info "repos count" (count repo-ids))
    (deliver result-promise repo-ids)))

(defn fetch [app-config result-promise]
  (http/get (-> app-config :url :repositories)
            (conj (:request-options app-config) {:query-params  {:includeLinks false}
                                                 :includeHidden true})
            (fn [response] (handle-fetch-success! response result-promise))
            http-helper/handle-exception))
