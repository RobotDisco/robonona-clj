;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack.mock
  (:require [robot-disco.robonona.slack.protocol :as slack]
            [clojure.spec.alpha :as s]))

(defrecord MockClient [state]
  slack/Client
  (get-channel-users [_ channel-id]
    (get-in @state [:channels channel-id :users]))
  (get-conversation-id [_ members]
    (get-in @state [:matches (set members) :channel]))
  (post-message [_ _ text]
    text))

(defn set-channel-users!
  "Set user ID list for mock slack state."
  [mock channel-id users]
  (swap! (:state mock) assoc-in [:channels channel-id :users] users))

(s/fdef set-channel-users!
  :args (s/cat
         :mock #(instance? MockClient %)
         :channel-id ::slack/channel-id
         :user-ids ::slack/users)
  :ret map?
  :fn (s/and #(contains? (:ret %) :channels)
             #(s/valid? ::slack/users (get-in (:ret %) [:channels (-> % :args :channel-id)]))))

(defn set-match-conversation!
  "Set channel ID that represents the pairing of two members"
  [mock members channel-id]
  (swap! (:state mock) assoc-in [:matches (set members) :channel] channel-id))

(s/fdef set-match-conversation!
  :args (s/cat
         :mock #(instance? MockClient %)
         :members ::slack/users
         :channel-id ::slack/channel-id)
  :ret map?
  :fn (s/and #(contains? (:ret %) :matches)
             #(s/valid? ::slack/channel-id (get-in (:ret %) [:matches (-> % :args :members set) :channel]))))
