(ns artist-time-proof.cli
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.spec.alpha :as spec]))

;; eg. jondoe
(spec/def ::auth-user
  #(and string? (not (.contains % "@"))))

;; eg. a**************************************************b
(spec/def ::auth-pass
  #(and string? (= (count %) 52)))

;; eg. microsoft
(spec/def ::azure-org string?)

;; eg. Jon Doe
(spec/def ::full-name
  #(and string? (not (.contains % "@"))))

(def cli-options
  [["-u" "--user USER" "Azure authentication user, eg. jondoe"
    :validate [#(spec/valid? ::auth-user %)]]
   ["-p" "--pass PASSWORD" "Azure authentication password, eg. a**************************************************b"
    :validate [#(spec/valid? ::auth-pass %)]]
   ["-o" "--org ORGANIZATION" "Azure organization, eg. microsoft"
    :validate [#(spec/valid? ::azure-org %)]]
   ["-f" "--full-name FIRST NAME AND SURNAME" "Author's name shown on report, eg. Jon Doe"
    :validate [#(spec/valid? ::full-name %)]]
   ["-h" "--help"]])

(defn- prepare-help [options-summary]
  (->> ["App for generating proof of creative work done to comply with Polish Labor Law."
        ""
        "Usage: artist-time-proof [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- prepare-error-msg [errors]
  (str "The following errors occurred while parsing your command:\n * "
       (string/join "\n * " errors)))

(defn process-args [args]
  (let [{:keys [options summary errors]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (prepare-help summary)}
      errors {:exit-message (prepare-error-msg errors)}
      options {:options options})))