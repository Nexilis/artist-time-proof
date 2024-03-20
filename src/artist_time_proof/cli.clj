(ns artist-time-proof.cli
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.spec.alpha :as spec]
            [clj-time.format :as f]
            [clj-time.core :as t]))

;; eg. jondoe, emails not allowed
(spec/def ::auth-user
  #(and string?
        (> (count %) 0)
        (not (.contains % "@"))))

;; eg. a**************************************************b, exactly 52 characters
(spec/def ::auth-pass
  #(and string?
        (= (count %) 52)))

;; eg. microsoft
(spec/def ::azure-org
  #(and string?
        (> (count %) 0)))

;; eg. Jon Doe, emails not allowed
(spec/def ::full-name
  #(and string?
        (> (count %) 0)
        (not (.contains % "@"))))

;; eg. 2019-11-01, future dates not allowed
(spec/def ::past-date #(t/after? (t/now) %))

(def cli-options
  [["-u" "--user USER" "[REQUIRED] Azure authentication user, eg. jondoe"
    :validate [#(spec/valid? ::auth-user %)]]
   ["-p" "--pass PASSWORD" "[REQUIRED] Azure authentication password, eg. a**************************************************b"
    :validate [#(spec/valid? ::auth-pass %)]]
   ["-o" "--org ORGANIZATION" "[REQUIRED] Azure organization, eg. microsoft"
    :validate [#(spec/valid? ::azure-org %)]]
   ["-f" "--full-name FIRST NAME AND SURNAME" "[REQUIRED] Author's name shown on report, eg. Jon Doe"
    :validate [#(spec/valid? ::full-name %)]]
   ["-F" "--date-from YYYY-MM-DD" "Only events from after this date will be included, eg. 2019-10-01"
    :default (t/minus (t/now) (t/months 1))
    :parse-fn #(f/parse (f/formatters :date) %)
    :validate [#(spec/valid? ::past-date %)]]
   ["-T" "--date-to YYYY-MM-DD" "Only events from before this date will be included, eg. 2019-11-01"
    :default (t/now)
    :parse-fn #(f/parse (f/formatters :date) %)
    :validate [#(spec/valid? ::past-date %)]]
   ["-h" "--help"]])

(defn- usage [summary]
  (->> ["App for generating proof of creative work done to comply with Polish Labor Law."
        ""
        "Usage: artist-time-proof [options]"
        ""
        "Options:"
        summary]
       (string/join \newline)))

(defn- required [summary options]
  (str (usage summary) "\n\nRequired options missing:"
       (if (not (contains? options :user))
         (string/join "\n -USER"))
       (if (not (contains? options :pass))
         (string/join "\n -PASSWORD"))
       (if (not (contains? options :pass))
         (string/join "\n -ORGANIZATION"))
       (if (not (contains? options :pass))
         (string/join "\n -FULL NAME"))))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n * "
       (string/join "\n * " errors)))

(defn- has-all-req-options? [options]
  (and (contains? options :user)
       (contains? options :pass)
       (contains? options :org)
       (contains? options :full-name)))

(defn process-args [args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) {:continue? false :errors? false :exit-message (usage summary)}
      errors {:continue? false :errors? true :exit-message (error-msg errors)}
      (not (has-all-req-options? options)) {:continue? false :errors? true :exit-message (required summary options)}
      options {:continue? true :errors? false :options options}
      :else {:continue? false :errors? true :exit-message (usage summary)})))
