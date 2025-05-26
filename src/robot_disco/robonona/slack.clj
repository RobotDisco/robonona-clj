;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

(s/def ::user-id string?)
(s/def ::channel-id string?)
(s/def ::users (s/coll-of ::user-id))

(s/def ::ok boolean?)
(s/def ::next_cursor (s/nilable string?))
(s/def ::response-metadata (s/keys :req-un [::next_cursor]))
(s/def ::response (s/keys :req-un [::ok] :opt-un [::users ::response_metadata]))

(defprotocol Client
  (get-channel-users
    "Return collection of member user IDs for the channel specified by its ID."
    [this channel-id]))

(defn- validate-response
  [response]
  (let [conformed (s/conform ::response response)]
    (if (s/invalid? conformed)
      (throw (ex-info "Invalid Slack Response" (s/explain-data ::response response)))
      conformed)))
