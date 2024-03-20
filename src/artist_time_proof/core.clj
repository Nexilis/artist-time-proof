(ns artist-time-proof.core
  (:require
   [artist-time-proof.pull-requests :as prs]
   [artist-time-proof.commits :as commits]
   [artist-time-proof.pdf-generation :as pdf-gen]
   [artist-time-proof.cli :as cli]
   [clojure.core.async :as async :exclude [map into reduce merge take transduce partition partition-by]]
   [taoensso.timbre :as timbre])
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
    (async/go (prs/fetch app-config))
    (async/go (commits/fetch app-config))
    (pdf-gen/generate-doc (:full-name options) app-config)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (time
   (do
     (timbre/info "Program START")
     (let [{:keys [continue? errors? options exit-message]} (cli/process-args args)]
       (if continue?
         (load-all options)
         (exit (if errors? 0 1) exit-message)))
     (timbre/info "Program END"))))
