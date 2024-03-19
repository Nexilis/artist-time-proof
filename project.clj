(defproject artist-time-proof "0.3.0"
  :description "A console app for delivering proof of working creatively to comply with Polish Labor Law."
  :url "https://github.com/Nexilis/artist-time-proof"
  :license {:name "GNU GENERAL PUBLIC LICENSE Version 3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/core.async "1.6.681"]
                 [org.clojure/core.specs.alpha "0.3.70"]
                 [cheshire "5.12.0"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.3"]
                 [clj-pdf "2.6.8"]
                 [proto-repl "0.3.1"]
                 [com.taoensso/timbre "6.5.0"]]
  :main ^:skip-aot artist-time-proof.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
