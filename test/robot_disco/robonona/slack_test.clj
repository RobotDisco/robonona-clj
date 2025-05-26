;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [robot-disco.robonona.slack :as slack]
            [robot-disco.robonona.slack-mock :as mock]))

(deftest channel-users-test
  (let [slack (mock/->MockClient)]
    (testing "Returns valid user collection"
      (let [users (slack/get-channel-users slack "C123")]
        (is (s/valid? ::slack/users users))))))
