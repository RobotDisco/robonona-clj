;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot
  (:require
   [clojure.edn :as edn]
   [robot-disco.robonona.matcher :as match]
   [robot-disco.robonona.slack.protocol :as slack]
   [robot-disco.robonona.slack.http-client :as client]))

(defn coffeebot [token channel-id]
  (let [client (client/->HttpClient token)
        users (slack/get-channel-users client channel-id)
        state (:data (edn/read-string (slurp "state.edn")))
        matches (if-let [state-first (first state)]
                  (if (= (into #{} users) (into #{} (conj ((comp flatten ::match/matched-pairs) state-first) (::match/unmatched-item state-first))))
                    ;; Rather than do true random, take the existing date. and rotate names
                    (do (prn true)
                        (drop 1 state))
                    ;; Channel roster has changed, so regenerate pairing
                    (do (prn false)
                        (match/round-robin-match-items users)))
                  (do (prn false)
                      (match/round-robin-match-items users)))
        res {:date (new java.util.Date)
             :data matches}]
    (spit "state.edn" (pr-str res))
    res))
    ;; Create conversations for each pair

    ;; Create a consolation for any unmatched user

(defn -main [& _]
  (coffeebot (System/getenv "SLACK_TOKEN") (System/getenv "SLACK_CHANNEL")))

;; Like in Python, check if this was a script run directly, and
;; run the main function if that happens.
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

;;;;;;; Dev Shit
(into #{} (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL")))
(let [{pairs ::match/matched-pairs unmatched ::match/unmatched-item} (match/match-items (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL")))] (into #{} (conj (flatten pairs) unmatched)))

(==  (into #{} (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL"))) (let [{pairs ::match/matched-pairs unmatched ::match/unmatched-item} (match/match-items (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL")))] (into #{} (conj (flatten pairs) unmatched))))

(=  (into #{} (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL"))) (let [{pairs ::match/matched-pairs unmatched ::match/unmatched-item} (match/match-items (slack/get-channel-users (http/->HttpClient (System/getenv "SLACK_TOKEN")) (System/getenv "SLACK_CHANNEL")))] (into #{} (conj (flatten pairs) unmatched))))
true
