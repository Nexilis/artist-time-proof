(ns artist-time-proof.core
  (:require
    [clj-http.client :as client]
    [clojure.tools.cli :as cli])
  (:gen-class))

(def azure-config {:organization "c*********"
                   :team-project "C*********"
                   :area "some-area"
                   :resource "some-resource"
                   :version 4.1})

(defn- azure-dev-url []
  (str "https://dev.azure.com/"
    (azure-config :organization)
    "/"
    (azure-config :team-project)))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (azure-dev-url)))
