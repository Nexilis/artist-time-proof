(defproject artist-time-proof "0.1.0-SNAPSHOT"
  :description "A console app for delivering proof of creative work to comply with Polish Labor Law."
  :url "https://github.com/Nexilis/artist-time-proof"
  :license {:name "GNU GENERAL PUBLIC LICENSE Version 3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [cheshire "5.8.1"]
                 [clj-http "3.9.1"]
                 [proto-repl "0.3.1"]]
  :main ^:skip-aot artist-time-proof.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
