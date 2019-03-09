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

(defn take-or-timeout!! [channel]
  "Takes data from a channel or timeouts after 2 seconds."
  (let [[take-result take-source] (alts!! [channel (timeout 2000)])]
    (if take-result
      (println "DEBUG taken from channel" take-source)
      (println "DEBUG channel timeout"))
    take-result))

;; Configurations
(def azure-base-url
  (format "https://dev.azure.com/%s/"
          (azure-config :organization)))

(def default-http-opts {:basic-auth [(auth :user) (auth :pass)]
                        :async?     true})
(def date-range (t/interval (t/minus (t/now) (t/months 1))
                            (t/now)))

;; HTTP
(defn handle-exception [exception]
  (println "DEBUG ERROR" exception))

(defn extract-value-from [response]
  ((json/parse-string (:body response) true) :value))


;; Commits
(def commits-response-count (atom 0))

(defn close-commits-chan! [callback-no]
  (close! commits-chan)
  (println "DEBUG closed commits-chan in callback no" callback-no))

(defn put-on-commits-chan! [response callback-no]
  (put! commits-chan (extract-value-from response))
  (println "DEBUG put on commits-chan in callback no" callback-no))

(defn handle-commits-fetch-success [response repos-count]
  (swap! commits-response-count inc)
  (let [callback-no (deref commits-response-count)]
    (println "DEBUG commits callback no" callback-no)
    (put-on-commits-chan! response callback-no)
    (if (= callback-no repos-count)
      (close-commits-chan! callback-no))))

(defn fetch-commits [repo-ids]
  (doseq [repo-id repo-ids]
    (let [url (str azure-base-url "_apis/git/repositories/" repo-id "/commits")
          repos-count (count repo-ids)]
      (client/get url
                  default-http-opts ;; TODO: filter with dates and author
                  (fn [response] (handle-commits-fetch-success response
                                                               repos-count))
                  handle-exception))))

(def url-repositories (str azure-base-url "_apis/git/repositories"))

(defn fetch-repositories [result-promise]
  (client/get url-repositories
              ;; TODO: includeHidden: true, includeLinks: false, consider handling paging
              default-http-opts
              (fn [response]
                (let [decoded-body (json/parse-string (:body response) true)
                      repo-ids (map :id (decoded-body :value))]
                  (deliver result-promise repo-ids)))
              handle-exception))

(defn load-commits []
  (let [repo-ids-promise (promise)]
    (fetch-repositories repo-ids-promise)
    (println "DEBUG repo-ids")
    (let [repo-ids (deref repo-ids-promise)]
      (println "DEBUG repos count"(count repo-ids))
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

(def url-pull-requests (str azure-base-url "_apis/git/pullrequests"))

(def pull-requests-response-count (atom 0))

(defn close-pull-requests-chan! [callback-no]
  (close! pull-requests-chan)
  (println "DEBUG closed pull-requests-chan in callback no" callback-no))

(defn put-on-pull-requests-chan! [response callback-no]
  (put! pull-requests-chan
        (filter filter-pull-requests
                (extract-value-from response)))
  (println "DEBUG put on pull-requests-chan in callback no" callback-no))

(defn handle-prs-fetch-success [response]
  (swap! pull-requests-response-count inc)
  (let [callback-no (deref pull-requests-response-count)]
    (println "DEBUG pull request callback no" callback-no)
    (put-on-pull-requests-chan! response callback-no)
    (if (= callback-no 2)
      (close-pull-requests-chan! callback-no))))

(defn single-pull-request-fetch [options]
  (client/get url-pull-requests
              options
              handle-prs-fetch-success
              handle-exception))

(defn fetch-pull-requests [user-id]
  (let [options [(conj default-http-opts {:query-params {:status "All" :creatorId user-id}})
                 (conj default-http-opts {:query-params {:status "All" :reviewerId user-id}})]]
    (doall (map single-pull-request-fetch options))))

(def url-user-id (str azure-base-url "_apis/connectionData"))

(defn fetch-user-id [result-promise]
  (client/get url-user-id
              default-http-opts
              (fn [response]
                (let [decoded-body (json/parse-string (:body response) true)
                      user-id (-> decoded-body :authenticatedUser :id)]
                  (deliver result-promise user-id)))
              handle-exception))

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
      [:phrase (str "(" pr-id ") ")]
      [:anchor
         {:style {:color [0 0 200]}
          :target pr-url}
         pr-title]]
     [:paragraph
      [:phrase (str "Created: " pr-created " | Closed: " pr-closed)]]]))

(defn build-pr-chapter []
  (loop [result []]
    (let [pr-seq (take-or-timeout!! pull-requests-chan)]
      (if pr-seq
        (let [prs-from-one-repository
              (doall
                (reduce
                  (fn [accumulator x]
                    (conj accumulator (build-pr-paragraph x)))
                  []
                  pr-seq))
              updated-result
              (conj result prs-from-one-repository)]
          (recur updated-result))
        (flatten-1 (flatten-1 result))))))

(defn build-commits-chapter []
  (loop [commits (<!! commits-chan)
         result [[:chapter "COMMITS"]]]
    (if commits
      ;; TODO: fix next few lines // construct chapter in the correct way
      (let [updated-result (conj result [:paragraph (str commits)])]
        (recur (<!! commits-chan) updated-result))
      result)))

(def pdf-config [{:title "Artist Time Proof"
                  :size "a4"
                  :footer "page"
                  :left-margin   25
                  :right-margin  25
                  :top-margin    35
                  :bottom-margin 35}])

(def file-name (str "artist-time-proof" (f/unparse (f/formatters :date-time) (t/now)) ".pdf"))

(defn present-results []
  (let [pdf-body (build-pr-chapter)
        pdf-whole (conj pdf-config pdf-body)]
    (println "DEBUG pdf-whole")
    ;(pp/pprint pdf-whole)
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