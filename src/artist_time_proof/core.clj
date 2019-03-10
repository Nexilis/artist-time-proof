(ns artist-time-proof.core
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [artist-time-proof.pdf-generation :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]])
  (:gen-class))

(defn load-all []
  (go (load-pull-requests))
  (go (load-commits))
  (present-results)
  (println "DEBUG Document generation done"))

(defn -main [& _]
  (println "DEBUG Program start")
  (time (load-all))
  (println "DEBUG Program end"))