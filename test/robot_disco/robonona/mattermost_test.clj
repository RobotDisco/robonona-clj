(ns robot-disco.robonona.mattermost-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer [deftest testing is]]

            [clj-http.client :as http]
            
            [robot-disco.robonona.mattermost :as SUT]))

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
        (is (spec/valid? ::SUT/users result))))))



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

