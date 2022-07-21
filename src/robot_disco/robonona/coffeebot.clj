(ns robot-disco.robonona.coffeebot
  (:require [clojure.spec.alpha :as spec]
            [cheshire.core :as json]
            [robot-disco.robonona.mattermost :as mattermost]
            [robot-disco.robonona.mattermost.user :as-alias user]))


;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(spec/def ::matched-pair (spec/tuple ::user/user ::user/user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user/user)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))


;;; Pairing Logic
;;;;;;;;;;;;;;;;;

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  (let [shuffled (shuffle (map #(select-keys % [::user/id ::user/username]) users))]
    (if (even? (count shuffled))
      ;; For some reason we need vectors here to conform to `spec/tuple`
      {::matched-pairs (map vec (partition 2 shuffled))}
      {::matched-pairs (map vec (partition 2 (drop 1 shuffled)))
       ::unmatched-user (first shuffled)})))

(spec/fdef match-users
  :args (spec/cat :users (spec/coll-of ::user/user))
  :ret ::matches
  ;; Length of pairs should be approximately half the length of candidate users
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (spec/and #(= (quot (count (-> % :args :users)) 2)
               (count (-> % :ret ::matched-pairs)))
            #(if (even? (count (-> % :args :users)))
              (not (contains? (:ret  %) ::unmatched-user))
              (contains? (:ret %) ::unmatched-user))))


;;; Messaging Logic
;;;;;;;;;;;;;;;;;;;


(def matched-message
  "Hello! This week you have been matched up as conversation partners! I hope you meet up and have a great time :)")

(def unmatched-message
  "Sorry! :( This week you haven't been matched with anyone. Better luck next week!")


(defn message-unmatched-user
  [bot-id user message]
  (::mattermost/success (mattermost/message-user bot-id user message)))

(spec/fdef message-unmatched-user
  :args (spec/cat :bot ::user/user :user ::user/user)
  :ret boolean?)


;;; Entrypoint
;;;;;;;;;;;;;;

(defn run
  [host token team channel dry-run]
  (let [bot-info (mattermost/get-my-info)
        channel-id (mattermost/channel-id-by-team-name-and-channel-name team
                                                                        channel)
        users (mattermost/active-users-by-channel-id channel-id)
        {:keys [::matched-pairs ::unmatched-user]
         :as result} (match-users users)]
    (if dry-run
      (do
        (println (json/generate-string
                  result {:pretty true}))
        result))))




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




  ) ;; End of comment

(comment

  (spec/valid? ::mattermost-user {:id "34ib5j6khbfjebfjgb356hjdhg"
                                  :username "gaelan.dcosta"})
  ;; => true

  (spec/valid? ::user {::user-id "34ib5j6khbfjebfjgb356hjdhg"
                       ::username "gaelan.dcosta"})
  ;; => true


  
  ) ;; Comment ends here
