(ns artist-time-proof.commits)

;https://dev.azure.com/{organization}/{project}/_apis/git/repositories/{repositoryId}/commits?searchCriteria.author={searchCriteria.author}&searchCriteria.toDate={searchCriteria.toDate}&searchCriteria.fromDate={searchCriteria.fromDate}&api-version=4.1


(def sample-commits-from-single-repo
  {:count 3,
   :value [{:commitId "c-id1",
            :author {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-16T08:27:33Z"},
            :committer {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-16T08:27:33Z"},
            :comment "some text 1",
            :commentTruncated true,
            :changeCounts {:Add 1, :Edit 3, :Delete 2},
            :url "url-x1",
            :remoteUrl "url-x1"}
           {:commitId "c-id2",
            :author {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-16T07:48:05Z"},
            :committer {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-16T07:48:05Z"},
            :comment "some text 2",
            :changeCounts {:Add 2, :Edit 1, :Delete 3},
            :url "url-x2",
            :remoteUrl "url-x2"}
           {:commitId "c-id3",
            :author {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-15T15:25:28Z"},
            :committer {:name "Bartek Łukasik", :email "email@test-test.test", :date "2018-05-15T15:25:28Z"},
            :comment "some text3",
            :commentTruncated true,
            :changeCounts {:Add 3, :Edit 2, :Delete 1},
            :url "url-x3",
            :remoteUrl "url-x3"}]})

(def sample-commits-from-all-repos [[] [{:commitId "c1"}] [] [{:commitId "c2"}] [{:commitId "c3"}] [] [] []])