(ns net.robot-disco.robonona.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [mattermost-clj.core :as mattermost]
            [mattermost-clj.api.channels :as channels]
            [mattermost-clj.api.users :as users]))

;; Information we need
(def token (System/getenv "ROBONONA_MATTERMOST_TOKEN"))
(def host "mattermost.internal.tulip.io")
(def team "General")
(def channel "coffeebot-everywhere")

(comment
  ;; Set up base info for connecting to mattermost's API
  (mattermost/set-api-context
   {:debug false
    :base-url (str "https://" host "/api/v4")
    :auths {"api_key" (str "Bearer " token)}})

  ;; What is the channel name I care about?
  (def channel-id (->> channel
                       (channels/teams-name-team-name-channels-name-channel-name-get team)
                       :id))

  (count (users/users-get {:page 0 :per-page 201 :active true :in-channel channel-id})))
