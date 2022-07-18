(ns robot-disco.robonona.coffeebot
  (:require [clojure.spec.alpha :as spec]
            [clojure.set]))


;;; Coffeebot data specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; It is likely we will be translating the returned API data into this form.
;; It is unclear to me whether these should live here or in some separate
;; mattermost translation module. It will likely depend on how "intelligent"
;; the translaction layer and/or impure side-effectful compoent should be.
(spec/def ::user-id string?)
(spec/def ::username string?)
(spec/def ::user (spec/keys :req [::user-id ::username]))
(spec/def ::users (spec/coll-of ::user))


;;; Mattermost data specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data as returned to us from mattermost APIs.
;;
;; This probably should live in its own class, handling mattermost data feels
;; like a unit of responsibility

(spec/def ::id ::user-id)
(spec/def ::mattermost-user (spec/keys :req-un [::id ::username]))


;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(spec/def ::matched-pair (spec/tuple ::user ::user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user)
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
  :args (spec/cat :users ::users)
  :ret ::matches
  ;; Length of pairs should be approximately half the length of candidate users
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (spec/and #(= (quot (count (-> % :args :users)) 2)
               (count (-> % :ret ::matched-pairs)))
            #(if (even? (count (-> % :args :users)))
              (not (contains? (:ret  %) ::unmatched-user))
              (contains? (:ret %) ::unmatched-user))))


(defn mattermost-user->user
  "Convert mattermost's user structure info coffeebot's user structure."
  [mm-user]
  (clojure.set/rename-keys mm-user
                           {:id ::user-id
                            :username ::username}))

(spec/fdef mattermost-user->user
  :args (spec/cat :mm-user ::mattermost-user)
  :ret ::user)



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
