(ns robot-disco.robonona.coffeebot-test
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.test.alpha :as spec-test]
   [clojure.test :refer [deftest is testing]]
   [robot-disco.robonona.coffeebot :as SUT]))

;; Mock data
;;;;;;;;;;;;

(def mock-user
  #:robot-disco.robonona.coffeebot
  {:user-id "aaa"
   :username "a@test.com"})

(def even-mock-user-list
  [mock-user mock-user])

(def odd-mock-user-list
  [mock-user mock-user mock-user])

(def mock-mattermost-user {:id "34ib5j6khbfjebfjgb356hjdhg"
                           :username "gaelan.dcosta"})

;;; Unit tests
;;;;;;;;;;;;;;

(deftest match-users
  (testing "even number of users"
    (let [users even-mock-user-list
          result (SUT/match-users even-mock-user-list)]
      (is (= (count (::SUT/matched-pairs result)) (/ (count users) 2)))
      (is (not (contains? result ::SUT/unmatched-user)))
      (is (spec/valid? ::SUT/matches result))))
  (testing "odd number of users"
    (let [users odd-mock-user-list
          result (SUT/match-users users)]
      (is (= (count (::SUT/matched-pairs result)) (/ (dec (count users)) 2)))
      (is (contains? result ::SUT/unmatched-user))
      (is (spec/valid? ::SUT/matches result))))
  (testing "generative testing"
    (is (= 1 (-> (spec-test/check `SUT/match-users)
                 (spec-test/summarize-results)
                 :check-passed)))))

(deftest mattermost-user->user
  (let [result (SUT/mattermost-user->user mock-mattermost-user)]
    (is (= result
           #::SUT
           {:user-id "34ib5j6khbfjebfjgb356hjdhg"
            :username "gaelan.dcosta"}))
    (is (spec/valid? ::SUT/user result))))

(comment

  ;; Moving files over broke my specifications b/c keywords.
  (spec/explain ::SUT/users [])

  (SUT/match-users even-mock-user-list)

  ;; Trying to figure out how `check` works
  ;; so I can embed it in my unit tests
  (spec-test/instrument `SUT/match-users)
  (spec-test/check `SUT/match-users)
  (spec-test/summarize-results (spec-test/check `SUT/match-users))



  ) ;; Comment ends here
