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

(defn mattermost-component
  "Create a mattermost component"
  [host token]
  ;; Set up base info for connecting to mattermost's API
  {:context {:debug false
             :base-url (str "https://" host "/api/v4")
             :auths {"api_key" (str "Bearer " token)}}})

(defn get-active-users
  "Given `team` and `channel` names, return list of active users"
  [component team channel]
  '({} {} {} {}))

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  {:matches '((() ())
              (() ())
              (() ())
              (() ()))
   :unmatched '()})

(deftest pair-users
  (let [token (System/getenv "ROBONONA_MATTERMOST_TOKEN")
        host "mattermost.internal.tulip.io"
        team "General"
        channel "coffeebot-everwhere"
        mm (mattermost-component host token)
        users (get-active-users mm team channel)
        matches (match-users users)]
    (is (= (count matches) (/ (count users) 2)))))

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

  (users/users-get {:page 0 :per-page 201 :active true :in-channel channel-id}))
