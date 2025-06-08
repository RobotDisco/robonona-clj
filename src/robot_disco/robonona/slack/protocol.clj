;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack.protocol
  (:require [clojure.spec.alpha :as s]))

(s/def ::user-id string?)
(s/def ::channel-id string?)
(s/def ::id ::channel-id)
(s/def ::users (s/coll-of ::user-id))

(s/def ::ok boolean?)
(s/def ::next_cursor (s/nilable string?))
(s/def ::response-channel (s/keys :req-un [::id]))
(s/def ::response-metadata (s/keys :req-un [::next_cursor]))
(s/def ::response (s/keys :req-un [::ok] :opt-un [::users ::response-channel ::response-metadata]))

(defprotocol Client
  (get-channel-users
    "Return collection of member user IDs for the channel specified by its ID."
    [this channel-id])
  (get-conversation-id
    "Return conversation ID that will create a conversation with two members."
    [this members])
  (post-message
    "Post message to slack channel."
    [this channel-id text]))

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
  :ret string?)

(defn validate-response
  [response]
  (let [conformed (s/conform ::response response)]
    (if (s/invalid? conformed)
      (throw (ex-info "Invalid Slack Response" (s/explain-data ::response response)))
      conformed)))
