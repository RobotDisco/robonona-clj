;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot-test
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as g]
            [clojure.spec.test.alpha :as spec-test]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [robot-disco.robonona.matcher :as match]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.http-client :as client]
            [robot-disco.robonona.slack.mock :as mock]))

;;; Functions to instrument
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn these on when developing or troubleshooting

(defn instrumentation-fixture [f]
  (spec-test/instrument)
  (f)
  (spec-test/unstrument))

(use-fixtures :once instrumentation-fixture)

(deftest match-user-test
  (testing "Test channel with even number of members"
    (let [slack (mock/->MockClient (atom {}))
          channel-id "C123AB456"
          user-ids ["U1" "U2" "U3" "U4"]]
      ;; Test pairing logic using mock client
      (mock/set-channel-users! slack channel-id user-ids)
      (let [result (-> (slack/get-channel-users slack channel-id)
                       match/random-match)]
        (is (s/valid? (s/coll-of (s/tuple ::slack/user-id ::slack/user-id))
                      (::match/matched-pairs result))))))
  (testing "Test channel with odd number of members"
    (let [slack (mock/->MockClient (atom {}))
          channel-id "C789CD012"
          user-ids ["U1" "U2" "U3" "U4" "U5"]]
        ;; Test pairing logic using mock client
      (mock/set-channel-users! slack channel-id user-ids)
      (let [result (-> (slack/get-channel-users slack channel-id)
                       match/random-match)]
        (is (s/valid? (s/coll-of (s/tuple ::slack/user-id ::slack/user-id))
                      (::match/matched-pairs result)))
        (is (s/valid? ::slack/user-id (::match/unmatched-user result)))))))

(deftest notify-matches
  (let [client (mock/->MockClient (atom {}))
        channel-id "C123"
        users ["U1" "U2" "U3" "U4" "U5"]]

    ;; Set up mock environment
    (mock/set-channel-users! client channel-id users)

    ;; Run our app logic
    (let [users (slack/get-channel-users client channel-id)
          matches (match/random-match users)
          {pairs ::match/matched-pairs
           unmatched ::match/unmatched-user} matches]

      ;; Verify specs of our result
      (is (s/valid? ::match/matches matches))
      (is (= 2 (count pairs)))
      (is (every? #(s/valid? (s/tuple ::slack/user-id ::slack/user-id) %) pairs))
      (is (s/valid? ::slack/user-id unmatched))

      ;; Fake conversation id for each pair
      (doseq [pair pairs]
        (mock/set-match-conversation! client pair (-> (s/gen ::slack/channel-id)
                                                      (g/sample 1)
                                                      first)))
      ;; Fake conversation id for unmatched
      (mock/set-match-conversation! client [unmatched] (-> (s/gen ::slack/channel-id)
                                                           (g/sample 1)
                                                           first))
      ;; Create conversations for each pair
      (let [convos (map (partial slack/get-conversation-id client) pairs)]
        (is (every? #(s/valid? ::slack/channel-id %) convos)))

      ;; Create a consolation for any unmatched user
      (when unmatched
        (let [convo (slack/get-conversation-id client [unmatched])]
          (is (s/valid? ::slack/channel-id convo)))))))

(deftest ^:integration create-convos-integration-test
  (when (System/getenv "SLACK_TOKEN")
    (when (System/getenv "SLACK_CHANNEL")
      (let [client (client/->HttpClient (System/getenv "SLACK_TOKEN"))
            users (slack/get-channel-users client (System/getenv "SLACK_CHANNEL"))
            matches (match/random-match users)
            {pairs ::match/matched-pairs
             unmatched ::match/unmatched-user} matches]

        ;; Verify specs of our result
        (is (s/valid? ::match/matches matches))
        (is (= (count users) (+ (count [unmatched]) (* 2 (count pairs)))))
        (is (every? #(s/valid? (s/tuple ::slack/user-id ::slack/user-id) %) pairs))
        (is (s/valid? ::slack/user-id unmatched))

        ;; Create conversations for each pair
        (let [convos (map (partial slack/get-conversation-id client) pairs)]
          (is (every? #(s/valid? ::slack/channel-id %) convos)))

        ;; Create a consolation for any unmatched user
        (when unmatched
          (let [convo (slack/get-conversation-id client [unmatched])]
            (is (s/valid? ::slack/channel-id convo))))))))
