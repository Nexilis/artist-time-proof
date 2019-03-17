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

(defn load-all [options]
  (let [http-config (build-http-config options)]
    (go (load-pull-requests http-config))
    (go (load-commits http-config)))
  (present-results (:full-name options)))

(defn -main [& args]
  (time
    (do
      (info "Program START")
      (let [{:keys [options exit-message]} (process-args args)]
        (if exit-message
          (println exit-message)
          (load-all options)))
      (info "Program END"))))