(ns artist-time-proof.conf
  (:require
    [clj-time.core :as t]
    [clj-time.format :as f]))

(def auth {:user "b*******@**********-***.**m"
           :pass "c**************************************************a"})

(def azure-config {:organization "c*********"
                   :team-project "C*********"
                   :from-date (f/unparse (f/formatters :date-time) (t/date-time 2018 10 01))
                   :to-date (f/unparse (f/formatters :date-time) (t/date-time 2018 11 01))
                   :git-author "Bartek Łukasik"})

; git update-index --assume-unchanged src/artist_time_proof/conf.clj
; git update-index --no-assume-unchanged src/artist_time_proof/conf.clj