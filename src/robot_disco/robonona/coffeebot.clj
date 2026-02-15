;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot
  (:require [robot-disco.robonona.history :as history]
            [robot-disco.robonona.matcher :as match]
            [robot-disco.robonona.slack.protocol :as slack]
            [robot-disco.robonona.slack.http-client :as client]))

;;; Messages
;;;;;;;;;;;;

(def matched-message
  "Hello! This week you have been matched up as conversation partners! I hope you meet up and have a great time :)")

(def unmatched-message
  "Sorry! :( This week you haven't been matched with anyone. Better luck next week!")

;;; Main logic
;;;;;;;;;;;;;;

(defn coffeebot
  "Run the coffeebot matching algorithm and send messages.

   Options:
   - :algorithm - :random (default) or :round-robin
   - :history-file - path to EDN file for round-robin history (required for round-robin)"
  [token channel-id & {:keys [algorithm history-file]
                       :or {algorithm :random}}]
  (let [client (client/->HttpClient token)
        users (slack/get-channel-users client channel-id)

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
    (let [convos (map (partial slack/get-conversation-id client) pairs)]
      (doseq [convo convos]
        (slack/post-message client convo matched-message)))

    ;; Create a consolation for any unmatched user
    (when unmatched
      (let [convo (slack/get-conversation-id client [unmatched])]
        (slack/post-message client convo unmatched-message)))

    ;; Save updated history if using round-robin
    (when (and (= algorithm :round-robin) history-file)
      (let [new-history (history/update-history pairing-history pairs now)
            new-state {::history/pairing-history new-history
                       ::history/last-run now}]
        (history/write-history history-file new-state)))))

(defn -main [& _]
  (let [token (System/getenv "SLACK_TOKEN")
        channel (System/getenv "SLACK_CHANNEL")
        algorithm (keyword (or (System/getenv "MATCH_ALGORITHM") "random"))
        history-file (System/getenv "HISTORY_FILE")]
    ;; Validate configuration
    (when (and (= algorithm :round-robin) (nil? history-file))
      (binding [*out* *err*]
        (println "Error: MATCH_ALGORITHM=round-robin requires HISTORY_FILE to be set"))
      (System/exit 1))
    (coffeebot token channel
               :algorithm algorithm
               :history-file history-file)))

;; Like in Python, check if this was a script run directly, and
;; run the main function if that happens.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
