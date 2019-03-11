(ns artist-time-proof.pdf-generation
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [artist-time-proof.http :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-pdf.core :as pdf]
    [clojure.pprint :as pp]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(def pdf-base [{:title         "Artist Time Proof"
                :size          "a4"
                :footer        "page"
                :left-margin   25
                :right-margin  25
                :top-margin    35
                :bottom-margin 35}
               [:paragraph {:size 20 :style :bold} "Copyrights report"]
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
  "Takes data from a channel or timeouts after 2 seconds."
  (let [[take-result take-source] (alts!! [channel (timeout 2000)])]
    (if take-result
      (debug "taken from" channel-name)
      (debug "timeout or closed" channel-name))
    take-result))

(defn- accumulate-single-commit [accumulator commit]
  (let [commit-id (:commitId commit)
        author (:author commit)
        author-date (-> commit :author :date)
        committer (:committer commit)
        comment (:comment commit)
        comment-truncated (:commentTruncated commit)
        changes-count (:changesCount commit)
        url (:url commit)
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
  (let [repository-name (-> pr :repository :name)
        last-merge-source-commit (:lastMergeSourceCommit pr)
        description (:description pr)
        repository (:repository pr)
        created-by (:createdBy pr)
        completion-options (:completionOptions pr)
        pull-request-id (:pullRequestId pr)
        closed-date (:closedDate pr)
        is-draft (:isDraft pr)
        completion-queue-time (:completionQueueTime pr)
        code-review-id (:codeReviewId pr)
        merge-id (:mergeId pr)
        supports-iterations (:supportsIterations pr)
        title (:title pr)
        target-ref-name (:targetRefName pr)
        status (:status pr)
        merge-status (:mergeStatus pr)
        url (:url pr)
        last-merge-target-commit (:lastMergeTargetCommit pr)
        source-ref-name (:sourceRefName pr)
        creation-date (:creationDate pr)
        last-merge-commit (:lastMergeCommit pr)
        reviewers (:reviewers pr)]
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

(defn- conj-commits-chapter [pdf]
  (loop [result (conj pdf [:paragraph {:size 20} "Commits"] [:line] [:spacer])]
    (let [data-from-chan (take-or-timeout!! commits-chan (name `commits-chan))]
      (if data-from-chan
        (let [updated-result
              (doall
                (reduce
                  (fn [accumulator x]
                    (accumulate-single-commit accumulator x))
                  result
                  data-from-chan))]
          (recur updated-result))
        result))))

(defn- conj-pull-requests-chapter [pdf]
  (loop [result (conj pdf [:paragraph {:size 20} "Pull Requests"] [:line] [:spacer])]
    (let [data-from-chan (take-or-timeout!! pull-requests-chan (name `pull-requests-chan))]
      (if data-from-chan
        (let [updated-result
              (doall
                (reduce
                  (fn [accumulator x]
                    (accumulate-single-pull-request accumulator x))
                  result
                  data-from-chan))]
          (recur updated-result))
        result))))

(defn present-results []
  (let [pdf-with-prs (conj-pull-requests-chapter pdf-base)
        pdf-whole (conj-commits-chapter pdf-with-prs)]
    (info "PDF generation START")
    (debug pdf-whole)
    (time (pdf/pdf pdf-whole pdf-file-name))
    (info "PDF generation END")))