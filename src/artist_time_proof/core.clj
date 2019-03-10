(ns artist-time-proof.core
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [artist-time-proof.pdf-generation :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]])
  (:gen-class))

(defn load-all []
  (go (load-pull-requests))
  (go (load-commits))
  (present-results))

(defn -main [& _]
  (info "Program START")
  (time (load-all))
  (info "Program END"))