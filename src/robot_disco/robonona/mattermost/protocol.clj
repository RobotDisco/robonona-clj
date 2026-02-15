;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.mattermost.protocol
  "Mattermost client protocol - mirrors slack/protocol for interchangeability."
  (:require [clojure.spec.alpha :as s]))

;;; Specs
;;;;;;;;;

(s/def ::user-id string?)
(s/def ::channel-id string?)
(s/def ::team-name string?)
(s/def ::channel-name string?)
(s/def ::users (s/coll-of ::user-id))

;;; Protocol
;;;;;;;;;;;;

(defprotocol Client
  "Mattermost client protocol - same interface as Slack for interchangeability."
  (get-channel-users
    [this channel-id]
    "Return collection of member user IDs for the channel specified by its ID.")
  (get-conversation-id
    [this members]
    "Return conversation ID (channel ID) for a DM/group conversation with the given members.")
  (post-message
    [this channel-id text]
    "Post message to the specified channel."))

;;; Function specs
;;;;;;;;;;;;;;;;;;

(s/fdef get-channel-users
  :args (s/cat :this any?
               :channel-id ::channel-id)
  :ret ::users)

(s/fdef get-conversation-id
  :args (s/cat :this any?
               :members ::users)
  :ret ::channel-id)

(s/fdef post-message
  :args (s/cat :this any?
               :channel-id ::channel-id
               :text string?)
  :ret any?)
