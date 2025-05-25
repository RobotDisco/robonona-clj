;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.coffeebot
  (:require [clojure.spec.alpha :as spec]
            [clojure.set :as set]
            [cheshire.core :as json]
            #_[robot-disco.robonona.mattermost :as mattermost]
            #_[robot-disco.robonona.mattermost.user :as-alias user]))

;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(spec/def ::user some?)
(spec/def ::matched-pair (spec/tuple ::user ::user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))

;;; Pairing Logic
;;;;;;;;;;;;;;;;;

(defn- remove-ignored-users
  [users ignore]
  (let [ignore-ids (into #{} (map :user/id ignore))]
    (remove #(ignore-ids (:user/id %)) users)))

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  (let [shuffled (shuffle users)]
    (if (even? (count shuffled))
      ;; For some reason we need vectors here to conform to `spec/tuple`
      {::matched-pairs (map vec (partition 2 shuffled))}
      {::matched-pairs (map vec (partition 2 (drop 1 shuffled)))
       ::unmatched-user (first shuffled)})))

(spec/fdef match-users
  :args (spec/cat :coll (spec/coll-of ::user :distinct true))
  :ret ::matches
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (fn [{:keys [args ret]}]
        (let [users (:coll args)]
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

;;; Test data
;;;;;;;;;;;;;

(comment

  ;; auto-resolution will only work when working inside this namespace.

  (spec/valid? ::user {::user-id "aaa"
                       ::username "aaa@test.com"})
  ;; => true

  (spec/valid? ::users [{::user-id "aaa"
                         ::username "aaa@test.com"}
                        {::user-id "bbb"
                         ::username "bbb@test.com"}
                        {::user-id "ccc"
                         ::username "ccc@test.com"}])
  ;; => true

  (spec/explain ::user {::user-id "aaa"
                        ::username "aaa@test.com"})
  ;; => nil

  ;; it turns out only vectors can conform to `spec/tuple`, not seqs.
  (spec/valid? ::matched-pair [{::user-id "aaa"
                                ::username "aaa@test.com"}
                               {::user-id "bbb"
                                ::username "bbb@test.com"}])
  ;; => true

  (spec/valid? ::matched-pair ({::user-id "aaa"
                                ::username "aaa@test.com"}
                               {::user-id "bbb"
                                ::username "bbb@test.com"}))
  ;; => false
  )
;; End of comment

(comment

  #_(mattermost/set-api-context {:mattermost/base-url "https://mattermost.internal.tulip.io/api/v4"
                                 :mattermost/auth-token (System/getenv "ROBONONA_MATTERMOST_TOKEN")})

  #_(run "mattermost.internal.tulip.io"
         (System/getenv "ROBONONA_MATTERMOST_TOKEN")
         "general"
         "coffeebot-dev"
         :dry-run true))

;; Comment ends here
