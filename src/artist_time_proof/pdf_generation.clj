(ns artist-time-proof.pdf-generation
  (:require
    [artist-time-proof.pull-requests :refer :all]
    [artist-time-proof.commits :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-pdf.core :as pdf]
    [clojure.pprint :as pp]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]))

(def pdf-config [{:title         "Artist Time Proof"
                  :size          "a4"
                  :footer        "page"
                  :left-margin   25
                  :right-margin  25
                  :top-margin    35
                  :bottom-margin 35}])

(def pdf-file-name (str "artist-time-proof" (f/unparse (f/formatters :date-time) (t/now)) ".pdf"))

(defn- flatten-1
  "Flattens only the first level of a given sequence, e.g. [[1 2][3]] becomes
   [1 2 3], but [[1 [2]] [3]] becomes [1 [2] 3]."
  [seq]
  (if (or (not (seqable? seq)) (nil? seq))
    seq                                                     ; if seq is nil or not a sequence, don't do anything
    (loop [acc [] [elt & others] seq]
      (if (nil? elt) acc
                     (recur
                       (if (seqable? elt)
                         (apply conj acc elt)               ; if elt is a sequence, add each element of elt
                         (conj acc elt))                    ; if elt is not a sequence, add elt itself
                       others)))))

(defn- take-or-timeout!! [channel channel-name]
  "Takes data from a channel or timeouts after 2 seconds."
  (let [[take-result take-source] (alts!! [channel (timeout 2000)])]
    (if take-result
      (debug "taken from" channel-name)
      (debug "timeout or closed" channel-name))
    take-result))

(defn- build-commits-paragraph [pr]
  ;; TODO: build proper commits paragraph
  (let [pr-repo-name (:repository :name pr)
        pr-id (:pullRequestId pr)
        pr-url (:url pr)
        pr-title (:title pr)
        pr-created (:creationDate pr)
        pr-closed (:closedDate pr)]
    [[:paragraph
      [:phrase (str "(" pr-id ") ")]
      [:anchor
       {:style  {:color [0 0 200]}
        :target pr-url}
       pr-title]]
     [:paragraph
      [:phrase (str "Created: " pr-created " | Closed: " pr-closed)]]]))

(defn- build-commits-chapter []
  (loop [result []]
    (let [commits-seq (take-or-timeout!! commits-chan (name `commits-chan))]
      (if commits-seq
        (let [commits-from-one-repo
              (doall
                (reduce
                  (fn [accumulator x]
                    (conj accumulator (build-commits-paragraph x)))
                  []
                  commits-seq))
              updated-result
              (conj result commits-from-one-repo)]
          (recur updated-result))
        (flatten-1 (flatten-1 result))))))

(defn- build-pr-paragraph [pr]
  (let [;pr-repo-name (:repository :name pr)
        pr-id (:pullRequestId pr)
        pr-url (:url pr)
        pr-title (:title pr)
        pr-created (:creationDate pr)
        pr-closed (:closedDate pr)]
    [[:paragraph
      [:phrase (str "(" pr-id ") ")]
      [:anchor
       {:style  {:color [0 0 200]}
        :target pr-url}
       pr-title]]
     [:paragraph
      [:phrase (str "Created: " pr-created " | Closed: " pr-closed)]]]))

(defn- build-pr-chapter []
  (loop [result []]
    (let [pr-seq (take-or-timeout!! pull-requests-chan (name `pull-requests-chan))]
      (if pr-seq
        (let [prs-from-one-repo
              (doall
                (reduce
                  (fn [accumulator x]
                    (conj accumulator (build-pr-paragraph x)))
                  []
                  pr-seq))
              updated-result
              (conj result prs-from-one-repo)]
          (recur updated-result))
        (flatten-1 (flatten-1 result))))))

(defn present-results []
  (let [pdf-body-prs (build-pr-chapter)
        pdf-body-commits (build-commits-chapter)
        pdf-whole (conj pdf-config
                        pdf-body-prs
                        pdf-body-commits)]
    (info "PDF generation START")
    (time (pdf/pdf pdf-whole pdf-file-name))
    (info "PDF generation END")))