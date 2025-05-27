;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [robot-disco.robonona.slack :as slack]
            [robot-disco.robonona.slack-mock :as mock]))

(deftest channel-users-test
  (let [slack (mock/->MockClient (atom {}))
        channel-id "C123"]
    (testing "Returns valid user collection"
      (mock/set-channel-users! slack "C123" ["U1" "U2" "U3"])
      (let [users (slack/get-channel-users slack channel-id)]
        (is (s/valid? ::slack/users users))))))
