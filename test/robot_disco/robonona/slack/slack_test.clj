;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack.slack-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as spec-test]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.mock :as mock]
            [robot-disco.robonona.slack.http-client :as client]))

;;; Functions to instrument
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn these on when developing or troubleshooting

(defn instrumentation-fixture [f]
  (spec-test/instrument)
  (f)
  (spec-test/unstrument))

(use-fixtures :once instrumentation-fixture)

(deftest channel-users-test
  (let [slack (mock/->MockClient (atom {}))
        channel-id "C123"]
    (testing "Returns valid user collection"
      (mock/set-channel-users! slack "C123" ["U1" "U2" "U3"])
      (let [users (slack/get-channel-users slack channel-id)]
        (is (s/valid? ::slack/users users))))))

(deftest ^:integration slack-integration-users-test
  (when (System/getenv "SLACK_TOKEN")
    (when (System/getenv "SLACK_CHANNEL")
      (let [slack (client/->HttpClient (System/getenv "SLACK_TOKEN"))]
        (testing "Real API call returns valid JSON"
          (is (s/valid? ::slack/users (slack/get-channel-users slack (System/getenv "SLACK_CHANNEL")))))))))
