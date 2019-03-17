(ns artist-time-proof.pdf-generation
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [artist-time-proof.http :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-pdf.core :as pdf]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(defn- build-pdf-base [full-name]
  [{:title                  "Artist Time Proof"
    :size                   "a4"
    :footer                 "page"
    :pages                  true
    :register-system-fonts? true
    :font                   {:encoding :unicode
                             :ttf-name "arial.ttf"}
    :left-margin            25
    :right-margin           25
    :top-margin             35
    :bottom-margin          35}
   [:paragraph {:size 20 :style :bold} (str "Copyrights report - " full-name)]
   [:spacer]
   [:paragraph
    [:phrase {:size 12}
     (str "For: " (f/unparse (f/formatters :date) today)
          " - " (f/unparse (f/formatters :date) month-ago))]]
   [:spacer 2]])

(def pdf-file-name (str "artist-time-proof-" (f/unparse (f/formatters :date-time) today) ".pdf"))

(def pdf-date-time-formatter (f/formatter "yyyy-MM-dd HH:MM"))

(defn- date-time->string [date-time]
  (f/unparse pdf-date-time-formatter (f/parse date-time)))

(defn- take-or-timeout!! [channel channel-name]
  "Takes data from a channel or timeouts after configured time."
  (let [[take-result] (alts!! [channel (timeout 5000)])]
    (if take-result
      (debug "taken from" channel-name)
      (debug "timeout or closed" channel-name))
    take-result))

(defn- accumulate-single-commit [accumulator commit]
  (let [commit-id (:commitId commit)
        author-date (-> commit :author :date)
        comment (:comment commit)
        remote-url (:remoteUrl commit)]
    (conj accumulator
          [:paragraph
           [:anchor
            {:style  {:color [0 0 200]}
             :target remote-url}
            comment]]
          [:paragraph
           [:phrase {:size 8}
            (str "Commit: " commit-id
                 " | Date: " (date-time->string author-date))]]
          [:paragraph
           [:phrase {:size 7}
            remote-url]]
          [:spacer])))

(defn- accumulate-single-pull-request [accumulator pr]
  (let [pull-request-id (:pullRequestId pr)
        closed-date (:closedDate pr)
        title (:title pr)
        url (:url pr)
        creation-date (:creationDate pr)]
    (conj accumulator
          [:paragraph
           [:phrase (str "(#" pull-request-id ") ")]
           [:anchor
            {:style  {:color [0 0 200]}
             :target url}
            title]]
          [:paragraph {:size 8}
           [:phrase (str "Created: " (date-time->string creation-date)
                         (if closed-date
                           (str " | Closed: "
                                (date-time->string closed-date))))]]
          [:paragraph {:size 7}
           [:phrase url]]
          [:spacer])))

(defn- conj-chapter [pdf chapter-name source-chan chan-name accumulate-function]
  (loop [result (conj pdf [:paragraph {:size 20} chapter-name] [:line] [:spacer])]
    (let [data-from-chan (take-or-timeout!! source-chan chan-name)]
      (if data-from-chan
        (let [updated-result
              (doall
                (reduce
                  (fn [accumulator x]
                    (accumulate-function accumulator x))
                  result
                  data-from-chan))]
          (recur updated-result))
        result))))

(defn present-results [full-name]
  (let [pdf-base (build-pdf-base full-name)
        pdf-with-prs (conj-chapter pdf-base
                                   "Pull Requests"
                                   pull-requests-chan
                                   (name `pull-requests-chan)
                                   accumulate-single-pull-request)
        pdf-with-all (conj-chapter pdf-with-prs
                                   "Commits"
                                   commits-chan
                                   (name `commits-chan)
                                   accumulate-single-commit)]
    (info "PDF generation START")
    (debug pdf-with-all)
    (time (pdf/pdf pdf-with-all pdf-file-name))
    (info "PDF generation END")))