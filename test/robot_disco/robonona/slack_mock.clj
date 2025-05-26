;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack-mock
  (:require [robot-disco.robonona.slack :as slack]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(defrecord MockClient []
  slack/Client
  (get-channel-users [_ _]
    (first (gen/sample (s/gen (s/coll-of ::slack/user-id) 1)))))
