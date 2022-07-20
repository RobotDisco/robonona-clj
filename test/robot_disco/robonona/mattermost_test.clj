(ns robot-disco.robonona.mattermost-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as spec-gen]
            [clojure.spec.test.alpha :as spec-test]
            [clojure.test :refer [deftest testing is use-fixtures]]

            [clj-http.client :as http]

            [robot-disco.robonona.mattermost.json :as-alias json]
            [robot-disco.robonona.mattermost.user :as-alias user]
            
            [robot-disco.robonona.mattermost :as SUT]))


;;; Functions to instrument
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn these on when developing or troubleshooting

(def ^:private tests-to-instrument
  `[SUT/active-users-by-channel-id
    SUT/json-user->user])


(defn instrumentation-fixture [f]
  (spec-test/instrument tests-to-instrument)
  (f)
  (spec-test/unstrument tests-to-instrument))

(use-fixtures :once instrumentation-fixture)

;;; Fake and mock data
;;;;;;;;;;;;;;;;;;;;;;


(def fake-token "faketoken")
(def fake-host "mattermost.test.com")

(def fake-channel-id "3462bshfgh567i86efgghsdgh4")
(def fake-response (list {:username "fake.user",
                          :id "5df6sdfgh4534bdfb342346dws"}
                         {:id "5qwdq6", :username "34EUwgaR"}
                         {:id "8pRMCiy34j0lm6iYy", :username "7K0S6y"}))

(deftest active-users-by-channel-id
  (testing "Happy path"
    (with-redefs [http/get (fn [_ _] {:body fake-response})]
      (let [result (SUT/active-users-by-channel-id fake-host
                                                   fake-token
                                                   fake-channel-id)]
        (is (spec/valid? (spec/coll-of ::user/user) result))))))

(def json-user-mock (spec-gen/generate (spec/gen ::json/user)))

(deftest json-user->user
  (let [result (SUT/json-user->user json-user-mock)]
    (is (= (::user/id result) (:id json-user-mock)))
    (is (= (::user/username result) (:username json-user-mock)))
    (is (spec/valid? ::user/user result))))

(deftest ^:generative json-user->user-generative
  (is (= 1 (-> (spec-test/check `SUT/json-user->user)
               (spec-test/summarize-results)
               :check-passed))))



;;; Generative testing
(comment
  ;; Maybe these should be their own suite of tests? Can I do that without
  ;; running them each time?

  ;; Can't test `active-users-by-channel-id` without faking a response, not
  ;; sure if it is worth it honestly as the real data logic lives elsewhere.
  (spec-test/check `[SUT/json-user->user])



  )  ;; Comment ends here


;;; Scratchpad
;;;;;;;;;;;;;;

(comment
  (require '[clojure.spec.alpha :as spec])
  (require '[clojure.spec.gen.alpha :as spec-gen])
  (require '[clojure.spec.test.alpha :as spec-test])
  [require '[clojure.test.check :as test-check]]

  (spec-gen/generate (spec/gen ::SUT/user))
  ;; => {:id "Hp685SU5xpJ928wkx8yulq1QsGk", :username "MMK786eQ777"}
  (spec-gen/generate (spec/gen ::SUT/user))

  (spec/conform ::SUT/users (list {:username "fake.user",
                                   :id "5df6sdfgh4534bdfb342346dws"}))

  (take 2 (spec-gen/generate (spec/gen ::SUT/users)))
  ;; => ({:id "5qwdq6", :username "34EUwgaR"}
  ;;     {:id "8pRMCiy34j0lm6iYy", :username "7K0S6y"})


  ) ;; Comment ends here

