(ns artist-time-proof.pdf-generation
  (:require
   [artist-time-proof.pull-requests :as prs]
   [artist-time-proof.commits :as commits]
   [clojure.core.async :as async :exclude [map into reduce merge take transduce partition partition-by]]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-pdf.core :as pdf]))

(defn- build-pdf-base [full-name app-config]
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
     (str "Data for period from: " (f/unparse (f/formatters :date) (:date-from app-config))
          " until: " (f/unparse (f/formatters :date) (:date-to app-config)))]]
   [:spacer 2]])

(def pdf-file-name (str "artist-time-proof-" (f/unparse (f/formatters :date-time) (t/now)) ".pdf"))

(def pdf-date-time-formatter (f/formatter "yyyy-MM-dd HH:MM"))

(defn- date-time->string [date-time]
  (f/unparse pdf-date-time-formatter (f/parse date-time)))

;; Takes data from a channel or timeouts after configured time.
(defn- take-or-timeout!! [channel]
  (let [[take-result] (async/alts!! [channel (async/timeout 5000)])]
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

(defn- conj-chapter [pdf chapter-name source-chan accumulate-function]
  (loop [result (conj pdf [:paragraph {:size 20} chapter-name] [:line] [:spacer])]
    (let [data-from-chan (take-or-timeout!! source-chan)]
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

(defn generate-doc [full-name app-config]
  (let [pdf-base (build-pdf-base full-name app-config)
        pdf-with-prs (conj-chapter pdf-base
                                   "Pull Requests"
                                   prs/pull-requests-chan
                                   accumulate-single-pull-request)
        pdf-with-all (conj-chapter pdf-with-prs
                                   "Commits"
                                   commits/commits-chan
                                   accumulate-single-commit)]
    (pdf/pdf pdf-with-all pdf-file-name)))
