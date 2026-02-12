;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot
  (:require [robot-disco.robonona.matcher :as match]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.http-client :as client]))

(defn coffeebot [token channel-id]
  (let [client (client/->HttpClient token)
        users (slack/get-channel-users client channel-id)
        matches (match/random-match users)
        {pairs ::match/matched-pairs
         unmatched ::match/unmatched-user} matches]

    (println matches)

    ;; Create conversations for each pair

    (let [convos (map (partial slack/get-conversation-id client) pairs)]
      (doseq [convo convos]
        (slack/post-message client convo "Hello! This week you have been matched up as conversation partners! I hope you meet up and have a great time :)")))

        ;; Create a consolation for any unmatched user
    (when unmatched
      (let [convo (slack/get-conversation-id client [unmatched])]
        (slack/post-message client convo "Sorry! :( This week you haven't been matched with anyone. Better luck next week!")))))

(defn -main [& _]
  (coffeebot (System/getenv "SLACK_TOKEN") (System/getenv "SLACK_CHANNEL")))

;; Like in Python, check if this was a script run directly, and
;; run the main function if that happens.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
