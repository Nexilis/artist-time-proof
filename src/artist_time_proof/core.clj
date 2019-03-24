(ns artist-time-proof.core
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [artist-time-proof.pdf-generation :refer :all]
    [artist-time-proof.cli :refer :all]
    [artist-time-proof.http :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]])
  (:gen-class))

(defn app-config [options]
  (let [url-base (format "https://dev.azure.com/%s/" (:org options))]
    {:author          (:user options)
     :url             {:connection-data (str url-base "_apis/connectionData")
                       :repositories    (str url-base "_apis/git/repositories")
                       :pull-requests   (str url-base "_apis/git/pullrequests")
                       :commits         (str url-base "_apis/git/repositories/repo-id/commits")}
     :request-options {:basic-auth [(:user options) (:pass options)]
                       :async?     true}
     :date-from       (:date-from options)
     :date-to         (:date-to options)}))

(defn load-all [options]
  (let [app-config (app-config options)]
    (go (load-pull-requests app-config))
    (go (load-commits app-config))
    (present-results (:full-name options) app-config)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (time
    (do
      (info "Program START")
      (let [{:keys [continue? errors? options exit-message]} (process-args args)]
        (if continue?
          (load-all options)
          (exit (if errors? 0 1) exit-message)))
      (info "Program END"))))