(ns artist-time-proof.core
  (:require
    [artist-time-proof.conf :refer :all]
    [clojure.core.async :refer :all :exclude [map into reduce merge take transduce partition partition-by]]
    [clj-http.client :as client]
    [cheshire.core :as json]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clj-pdf.core :as pdf]
    [clojure.pprint :as pp])
  (:gen-class))

(def pull-requests-chan (chan 2))
(def commits-chan (chan))


(defn flatten-1
  "Flattens only the first level of a given sequence, e.g. [[1 2][3]] becomes
   [1 2 3], but [[1 [2]] [3]] becomes [1 [2] 3]."
  [seq]
  (if (or (not (seqable? seq)) (nil? seq))
    seq ; if seq is nil or not a sequence, don't do anything
    (loop [acc [] [elt & others] seq]
      (if (nil? elt) acc
                     (recur
                       (if (seqable? elt)
                         (apply conj acc elt) ; if elt is a sequence, add each element of elt
                         (conj acc elt))      ; if elt is not a sequence, add elt itself
                       others)))))

;; Configurations
(def azure-base-url
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})
(def date-range (t/interval (t/date-time 2019 1 1) (t/date-time 2019 1 4)))


;; HTTP
(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))


;; Commits
(defn fetch-commits [repo-ids]
  (doseq [repo-id repo-ids]
    (let [url (str azure-base-url "_apis/git/repositories/" repo-id "/commits")
          opts default-http-opts]
      (client/get url
                  ;; TODO: filter with dates and author
                  opts
                  (fn [response] (put! commits-chan (extract-value-from response)))
                  handle-exception))))


(defn fetch-repositories [result-promise]
  (let [url (str azure-base-url "_apis/git/repositories")]
    (client/get url
                ;; TODO: includeHidden: true, includeLinks: false, consider handling paging
                default-http-opts
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        repo-ids (map :id (decoded-body :value))]
                    (deliver result-promise repo-ids)))
                handle-exception)))

(defn load-commits []
  (let [repo-ids-promise (promise)]
    (fetch-repositories repo-ids-promise)
    (println "DEBUG repo-ids")
    (let [repo-ids (deref repo-ids-promise)]
      (fetch-commits repo-ids))))

;; Pull Requests
(defn completed? [pr]
  (= (:status pr) "completed"))

(defn in-date-range? [date-time-string]
  (t/within? date-range (f/parse date-time-string)))

(defn filter-pull-requests [pr]
  (if (completed? pr)
    (in-date-range? (:closedDate pr))
    (in-date-range? (:creationDate pr))))

(defn fetch-pull-requests [user-id]
  (let [url (str azure-base-url "_apis/git/pullrequests")
        creator-opts (conj default-http-opts {:query-params {:status    "All"
                                                             :creatorId user-id}})
        reviewer-opts (conj default-http-opts {:query-params {:status     "All"
                                                              :reviewerId user-id}})]
    ;; TODO: close the channel here!
    ;; TODO: remove duplication, eg. https://www.clojure.org/guides/core_async_go
    (client/get url
                creator-opts
                (fn [response]
                  (put! pull-requests-chan (filter filter-pull-requests
                                                   (extract-value-from response))))
                handle-exception)
    (client/get url
                reviewer-opts
                (fn [response]
                  (put! pull-requests-chan (filter filter-pull-requests
                                                   (extract-value-from response))))
                handle-exception)))

(defn fetch-user-id [result-promise]
  (let [url (str azure-base-url "_apis/connectionData")]
    (client/get url
                default-http-opts
                (fn [response]
                  (let [decoded-body (json/parse-string (:body response) true)
                        user-id (-> decoded-body :authenticatedUser :id)]
                    (deliver result-promise user-id)))
                handle-exception)))

(defn load-pull-requests []
  (let [user-id-promise (promise)]
    (fetch-user-id user-id-promise)
    (let [user-id (deref user-id-promise)]
      (println "DEBUG user-id" user-id)
      (fetch-pull-requests user-id))))

;; Main
(defn build-pr-paragraph [pr]
  (let [pr-repo-name (:repository :name pr)
        pr-id (:pullRequestId pr)
        pr-url (:url pr)
        pr-title (:title pr)
        pr-created (:creationDate pr)
        pr-closed (:closedDate pr)]
    [[:paragraph
      [:phrase (str "(" pr-id ")")]
      [:anchor {:target pr-url} pr-title]]
     [:paragraph
      [:phrase (str "Created: " pr-created " | Closed: " pr-closed)]]]))

(defn build-pr-chapter []
  (loop [result []
         chan-read-count 1]
    (if (< chan-read-count 3)
      (let [pr-seq (<!! pull-requests-chan)
            prs-from-one-repository (doall (reduce (fn [accumulator x]
                                                     (conj accumulator (build-pr-paragraph x)))
                                                   []
                                                   pr-seq))
            updated-result (conj result prs-from-one-repository)]
        (if (= chan-read-count 2) (close! pull-requests-chan))
        (recur updated-result (inc chan-read-count)))
      (flatten-1 (flatten-1 result)))))

(defn build-commits-chapter []
  (loop [commits (<!! commits-chan)
         result [[:chapter "COMMITS"]]]
    (if commits
      ;; TODO: fix next few lines // construct chapter in the correct way
      (let [updated-result (conj result [:paragraph (str commits)])]
        (recur (<!! commits-chan) updated-result))
      result)))

(defn present-results []
  (let [pdf-config [{:title "Artist Time Proof"
                     :size "a4"
                     :footer "page"
                     :left-margin   15
                     :right-margin  15
                     :top-margin    20
                     :bottom-margin 20}]
        pdf-body (build-pr-chapter)
        pdf-whole (conj pdf-config pdf-body)
        file-name (str "artist-time-proof" (f/unparse (f/formatters :date-time) (t/now)) ".pdf")]
    (println "DEBUG pdf-whole")
    (pp/pprint pdf-whole)
    (pdf/pdf pdf-whole file-name)))

(defn load-all []
  (go
    (load-pull-requests))
  (go
    (load-commits))
  (present-results)
  (println "DEBUG Document generation done"))

(defn -main [& _]
  (println "DEBUG Program start")
  (time (load-all))
  (println "DEBUG Program end"))