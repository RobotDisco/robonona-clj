(ns robot-disco.robonona.coffeebot-test
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.test.alpha :as spec-test]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [robot-disco.robonona.coffeebot :as SUT]
   [robot-disco.robonona.mattermost.user :as-alias user]))


;;; Functions to instrument
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn these on when developing or troubleshooting

(def ^:private tests-to-instrument
  `[SUT/match-users
    SUT/message-matched-pairs])


(defn instrumentation-fixture [f]
  (spec-test/instrument tests-to-instrument)
  (f)
  (spec-test/unstrument tests-to-instrument))

(use-fixtures :once instrumentation-fixture)


;; Mock data
;;;;;;;;;;;;

(def mock-user
  #:robot-disco.robonona.mattermost.user
  {:id "aaa"
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
      (is (spec/valid? ::SUT/matches result)))))

(deftest ^:generative match-users-generative
  (is (= 1 (-> (spec-test/check `SUT/match-users)
               (spec-test/summarize-results)
               :check-passed))))


(deftest message-matched-users
  (testing "happy path"
    (let [user1 #::user{:id "7L", :username "343GF"}
          user2 #::user{:id "55", :username "6HS"}
          result (SUT/message-matched-users user1 user2)]
      (is (true? result)))))


(comment

  (require '[clojure.spec.gen.alpha :as spec-gen])

  ;; Moving files over broke my specifications b/c keywords.
  (spec/explain ::SUT/users [])

  (SUT/match-users even-mock-user-list)

  ;; Trying to figure out how `check` works
  ;; so I can embed it in my unit tests
  (spec-test/instrument `SUT/match-users)
  (spec-test/check `SUT/match-users)
  (spec-test/summarize-results (spec-test/check `SUT/match-users))

  (spec-gen/sample (spec/gen ::user/user))
  ;;     #:robot-disco.robonona.mattermost.user{:id "xb7c", :username ""}
  ;;     #:robot-disco.robonona.mattermost.user{:id "Gu2", :username "03r"}
  ;;     #:robot-disco.robonona.mattermost.user{:id "", :username "A7"}
  ;;     #:robot-disco.robonona.mattermost.user{:id "9Rz81", :username "OU5YBKo"}
  ;;     #:robot-disco.robonona.mattermost.user{:id "2LHe", :username "i9A8jU7P"}
  ;;     #:robot-disco.robonona.mattermost.user{:id "8q99D", :username "75VmBe"})

  ) ;; Comment ends here
