;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.matcher
  (:require [clojure.spec.alpha :as spec]
            [robot-disco.robonona.slack.protocol :as slack]))

;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(spec/def ::user-id ::slack/user-id)
(spec/def ::matched-pair (spec/tuple ::user-id ::user-id))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user-id)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))

;;; Random Pairing Logic
;;;;;;;;;;;;;;;;;;;;;;;;

(defn- remove-ignored-users
  [users ignore]
  (let [ignore-ids (into #{} (map :user/id ignore))]
    (remove #(ignore-ids (:user/id %)) users)))

(defn random-match
  "Randomly pair users by shuffling and grouping into pairs.
   If odd number of users, one user is returned as ::unmatched-user."
  [users]
  (let [shuffled (shuffle users)]
    (if (even? (count shuffled))
      ;; For some reason we need vectors here to conform to `spec/tuple`
      {::matched-pairs (map vec (partition 2 shuffled))}
      {::matched-pairs (map vec (partition 2 (drop 1 shuffled)))
       ::unmatched-user (first shuffled)})))

(spec/fdef random-match
  :args (spec/cat :users (spec/coll-of ::user-id))
  :ret ::matches
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (fn [{:keys [args ret]}]
        (let [users (:users args)]
          (if (even? (count users))
            (not (contains? ret ::unmatched-user))
            (contains? ret ::unmatched-user)))))

;;; Messaging Logic
;;;;;;;;;;;;;;;;;;;

(def matched-message
  "Hello! This week you have been matched up as conversation partners! I hope you meet up and have a great time :)")

(def unmatched-message
  "Sorry! :( This week you haven't been matched with anyone. Better luck next week!")

#_(defn message-unmatched-user
    "As `bot`, send `message` to `user`"
    [bot user message]
    (:mattermost/success (mattermost/message-user bot user message)))

#_(spec/fdef message-unmatched-user
    :args (spec/cat :bot :user/user :user :user/user :message string?)
    :ret boolean?)

#_(defn message-matched-pair
    [bot pair message]
    (:mattermost/success (mattermost/message-users (conj pair bot) message)))

#_(spec/fdef message-matched-pair
    :args (spec/cat :bot :user/user :pair ::matched-pair :message string?)
    :ret boolean?)

