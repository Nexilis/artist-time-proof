(ns artist-time-proof.core
  (:require
    [artist-time-proof.commits :refer :all])
  (:gen-class))

(defn -main [& _]
  (println (commits)))