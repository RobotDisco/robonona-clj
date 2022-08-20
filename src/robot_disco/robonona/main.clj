(ns robot-disco.robonona.main
  (:require [robot-disco.robonona.coffeebot :as coffeebot]
            [robot-disco.robonona.config :as config]
            [robot-disco.robonona.mattermost :as mattermost]))


;;; Entrypoint
;;;;;;;;;;;;;;

(defn run
  "Do the needful."
  [config]
  (let [host (config/mattermost-host config)
        token (config/mattermost-token config)
        team (config/mattermost-team config)
        channel (config/coffeebot-channel config)
        dry-run (config/coffeebot-dry-run config)
        _ (mattermost/set-api-context {
                                       ::mattermost/base-url (str "https://" host "/api/v4")
                                       ::mattermost/auth-token token})
        bot-info (mattermost/get-my-info)
        channel-id (mattermost/channel-id-by-team-name-and-channel-name team
                                                                        channel)
        users (mattermost/active-users-by-channel-id channel-id)
        {::coffeebot/keys [matched-pairs unmatched-user]
         :as result} (coffeebot/match-users users [bot-info])]
    (when (not dry-run)
      (when unmatched-user
        (coffeebot/message-unmatched-user bot-info
                                unmatched-user
                                coffeebot/unmatched-message))
      (doseq [pair matched-pairs]
        (coffeebot/message-matched-pair bot-info
                              pair
                              coffeebot/matched-message)))
    result))


(defn main
  "entrypoint"
  [& {:keys [config]}]
  (println (run (config/config config))))



(comment

  (config/config :dev)
  (run)

  )  ;; Comment ends here.
