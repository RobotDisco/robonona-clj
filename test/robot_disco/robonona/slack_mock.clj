;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack-mock
  (:require [robot-disco.robonona.slack :as slack]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(defrecord MockClient [state]
  slack/Client
  (get-channel-users [_ channel-id]
    (get-in @state [:channels channel-id :users])))

(defn set-channel-users!
  "Set user ID list for mock slack state."
  [mock channel-id users]
  (swap! (:state mock) assoc-in [:channels channel-id :users] users))

(s/fdef set-channel-users!
  :args (s/cat
         :mock #(instance? MockClient %)
         :channel-id ::slack/channel-id
         :user-ids ::slack/user-ids)
  :ret map?
  :fn (s/and #(contains? (:ret %) :channels)
             #(s/valid? ::slack/user-ids (get-in (:ret %) [:channels (-> % :args :channel-id)]))))
