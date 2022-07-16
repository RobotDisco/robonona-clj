(ns net.robot-disco.robonona.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [mattermost-clj.core :as mattermost]))

(comment
  (def token System/getenv "ROBONONA_MATTERMOST_TOKEN")
  (def host "mattermost.internal.tulip.io")

  (mattermost/set-apt-context
   {:debug false
    :base-url (str "https://" host "/api/v4")
    :auths {"api_key" (str "Bearer " token)}})

  ;; try calling an API
  (require '[mattermost-clj.api.teams :as teams])

  (teams/teams-get-with-http-info))
