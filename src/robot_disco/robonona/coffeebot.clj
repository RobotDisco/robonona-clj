(ns robot-disco.robonona.coffeebot
  (:require [clojure.spec.alpha :as spec]
            [robot-disco.robonona.mattermost]
            [robot-disco.robonona.mattermost.user :as-alias user]))


;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(spec/def ::matched-pair (spec/tuple ::user/user ::user/user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user/user)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))

;;; Application logic
;;;;;;;;;;;;;;;;;;;;;

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
  :args (spec/cat :users (spec/coll-of ::user/user))
  :ret ::matches
  ;; Length of pairs should be approximately half the length of candidate users
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (spec/and #(= (quot (count (-> % :args :users)) 2)
               (count (-> % :ret ::matched-pairs)))
            #(if (even? (count (-> % :args :users)))
              (not (contains? (:ret  %) ::unmatched-user))
              (contains? (:ret %) ::unmatched-user))))


(defn message-matched-users
  [user1 user2]
  true)

(spec/fdef message-matched-users
  :args (spec/cat :user1 ::user/user :user2 ::user/user)
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




  ) ;; End of comment

(comment

  (spec/valid? ::mattermost-user {:id "34ib5j6khbfjebfjgb356hjdhg"
                                  :username "gaelan.dcosta"})
  ;; => true

  (spec/valid? ::user {::user-id "34ib5j6khbfjebfjgb356hjdhg"
                       ::username "gaelan.dcosta"})
  ;; => true



  ) ;; Comment ends here
