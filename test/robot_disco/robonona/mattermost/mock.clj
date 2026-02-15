;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.mattermost.mock
  "Mock Mattermost client for testing - mirrors slack/mock.clj pattern."
  (:require [robot-disco.robonona.mattermost.protocol :as mattermost]
            [clojure.spec.alpha :as s]))

(defrecord MockClient [state]
  mattermost/Client
  (get-channel-users [_ channel-id]
    (get-in @state [:channels channel-id :users]))
  (get-conversation-id [_ members]
    (get-in @state [:matches (set members) :channel]))
  (post-message [_ _ text]
    text))

(defn set-channel-users!
  "Set user ID list for mock mattermost state."
  [mock channel-id users]
  (swap! (:state mock) assoc-in [:channels channel-id :users] users))

(s/fdef set-channel-users!
  :args (s/cat
         :mock #(instance? MockClient %)
         :channel-id ::mattermost/channel-id
         :user-ids ::mattermost/users)
  :ret map?
  :fn (s/and #(contains? (:ret %) :channels)
             #(s/valid? ::mattermost/users (get-in (:ret %) [:channels (-> % :args :channel-id)]))))

(defn set-match-conversation!
  "Set channel ID that represents the pairing of members."
  [mock members channel-id]
  (swap! (:state mock) assoc-in [:matches (set members) :channel] channel-id))

(s/fdef set-match-conversation!
  :args (s/cat
         :mock #(instance? MockClient %)
         :members ::mattermost/users
         :channel-id ::mattermost/channel-id)
  :ret map?
  :fn (s/and #(contains? (:ret %) :matches)
             #(s/valid? ::mattermost/channel-id (get-in (:ret %) [:matches (-> % :args :members set) :channel]))))
