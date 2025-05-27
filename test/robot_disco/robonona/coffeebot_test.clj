;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [robot-disco.robonona.matcher :as match]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.mock :as mock]))

(deftest coffeebot-test
  (testing "Test channel with even number of members"
    (let [slack (mock/->MockClient (atom {}))
          channel-id "C123AB456"
          user-ids ["U1" "U2" "U3" "U4"]]
      ;; Test pairing logic using mock client
      (mock/set-channel-users! slack channel-id user-ids)
      (let [result (-> (slack/get-channel-users slack channel-id)
                       match/match-items)]
        (is (s/valid? (s/coll-of (s/tuple ::slack/user-id ::slack/user-id))
                      (::match/matched-pairs result))))))
  (testing "Test channel with odd number of members"
    (let [slack (mock/->MockClient (atom {}))
          channel-id "C789CD012"
          user-ids ["U1" "U2" "U3" "U4" "U5"]]
        ;; Test pairing logic using mock client
      (mock/set-channel-users! slack channel-id user-ids)
      (let [result (-> (slack/get-channel-users slack channel-id)
                       match/match-items)]
        (is (s/valid? (s/coll-of (s/tuple ::slack/user-id ::slack/user-id))
                      (::match/matched-pairs result)))
        (is (s/valid? ::slack/user-id (::match/unmatched-item result)))))))
