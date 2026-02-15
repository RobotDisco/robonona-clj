;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot
  (:require [robot-disco.robonona.history :as history]
            [robot-disco.robonona.matcher :as match]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.http-client :as slack-client]
            [robot-disco.robonona.mattermost.protocol :as mattermost]
            [robot-disco.robonona.mattermost.http-client :as mattermost-client]))

;;; Messages
;;;;;;;;;;;;

(def matched-message
  "Hello! This week you have been matched up as conversation partners! I hope you meet up and have a great time :)")

(def unmatched-message
  "Sorry! :( This week you haven't been matched with anyone. Better luck next week!")

;;; Protocol dispatch
;;;;;;;;;;;;;;;;;;;;;
;; Both Slack and Mattermost protocols have identical method signatures.
;; These helper functions dispatch to the correct protocol based on client type.

(defn- get-channel-users [client channel-id]
  (if (instance? robot_disco.robonona.slack.http_client.HttpClient client)
    (slack/get-channel-users client channel-id)
    (mattermost/get-channel-users client channel-id)))

(defn- get-conversation-id [client members]
  (if (instance? robot_disco.robonona.slack.http_client.HttpClient client)
    (slack/get-conversation-id client members)
    (mattermost/get-conversation-id client members)))

(defn- post-message [client channel-id text]
  (if (instance? robot_disco.robonona.slack.http_client.HttpClient client)
    (slack/post-message client channel-id text)
    (mattermost/post-message client channel-id text)))

;;; Main logic
;;;;;;;;;;;;;;

(defn coffeebot
  "Run the coffeebot matching algorithm and send messages.

   Arguments:
   - client - A Slack or Mattermost client implementing the respective protocol
   - channel-id - The channel ID to fetch users from

   Options:
   - :algorithm - :random (default) or :round-robin
   - :history-file - path to EDN file for round-robin history (required for round-robin)"
  [client channel-id & {:keys [algorithm history-file]
                        :or {algorithm :random}}]
  (let [users (get-channel-users client channel-id)

        ;; Load history if using round-robin
        state (when (and (= algorithm :round-robin) history-file)
                (history/read-history history-file))
        pairing-history (or (::history/pairing-history state) {})

        ;; Select and run matching algorithm
        matches (case algorithm
                  :round-robin (match/round-robin-match users pairing-history)
                  (match/random-match users))

        {pairs ::match/matched-pairs
         unmatched ::match/unmatched-user} matches
        now (java.util.Date.)]

    (println matches)

    ;; Create conversations for each pair
    (let [convos (map (partial get-conversation-id client) pairs)]
      (doseq [convo convos]
        (post-message client convo matched-message)))

    ;; Create a consolation for any unmatched user
    (when unmatched
      (let [convo (get-conversation-id client [unmatched])]
        (post-message client convo unmatched-message)))

    ;; Save updated history if using round-robin
    (when (and (= algorithm :round-robin) history-file)
      (let [new-history (history/update-history pairing-history pairs now)
            new-state {::history/pairing-history new-history
                       ::history/last-run now}]
        (history/write-history history-file new-state)))))

(defn -main [& _]
  (let [chat-backend (keyword (or (System/getenv "ROBONONA_CHAT_BACKEND") "slack"))
        algorithm (keyword (or (System/getenv "ROBONONA_MATCH_ALGORITHM") "random"))
        history-file (System/getenv "ROBONONA_MATCH_HISTORY_FILE")]

    ;; Validate matching configuration
    (when (and (= algorithm :round-robin) (nil? history-file))
      (binding [*out* *err*]
        (println "Error: ROBONONA_MATCH_ALGORITHM=round-robin requires ROBONONA_MATCH_HISTORY_FILE to be set"))
      (System/exit 1))

    ;; Chat backend-specific configuration and execution
    (case chat-backend
      :slack
      (let [token (System/getenv "ROBONONA_SLACK_TOKEN")
            channel (System/getenv "ROBONONA_SLACK_CHANNEL")]
        (when (or (nil? token) (nil? channel))
          (binding [*out* *err*]
            (println "Error: ROBONONA_CHAT_BACKEND=slack requires ROBONONA_SLACK_TOKEN and ROBONONA_SLACK_CHANNEL"))
          (System/exit 1))
        (let [client (slack-client/->HttpClient token)]
          (coffeebot client channel
                     :algorithm algorithm
                     :history-file history-file)))

      :mattermost
      (let [url (System/getenv "ROBONONA_MATTERMOST_URL")
            token (System/getenv "ROBONONA_MATTERMOST_TOKEN")
            team (System/getenv "ROBONONA_MATTERMOST_TEAM")
            channel-name (System/getenv "ROBONONA_MATTERMOST_CHANNEL")]
        (when (or (nil? url) (nil? token) (nil? team) (nil? channel-name))
          (binding [*out* *err*]
            (println "Error: ROBONONA_CHAT_BACKEND=mattermost requires ROBONONA_MATTERMOST_URL, ROBONONA_MATTERMOST_TOKEN, ROBONONA_MATTERMOST_TEAM, and ROBONONA_MATTERMOST_CHANNEL"))
          (System/exit 1))
        (let [client (mattermost-client/->HttpClient url token team)
              ;; Mattermost uses channel names, need to look up the ID
              channel-id (mattermost-client/channel-id-by-name client channel-name)]
          (coffeebot client channel-id
                     :algorithm algorithm
                     :history-file history-file)))

      ;; Unknown backend
      (do
        (binding [*out* *err*]
          (println (str "Error: Unknown chat backend '" (name chat-backend) "'. Use 'slack' or 'mattermost'.")))
        (System/exit 1)))))

;; Like in Python, check if this was a script run directly, and
;; run the main function if that happens.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
