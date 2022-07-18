(ns net.robot-disco.robonona.coffeebot.design-journal
  (:require
   ;; Tests and specifications
   [clojure.test :refer [deftest is testing]]
   [clojure.spec.alpha :as spec]
   [clojure.test.check :as test-check]
   [clojure.spec.gen.alpha :as spec-gen]
   [clojure.spec.test.alpha :as spec-test]
   ;; Mattermost API client
   [mattermost-clj.core :as mattermost]
   [mattermost-clj.api.channels :as channels]
   [mattermost-clj.api.users :as users]))


;;;; 2022-07-16 - Sketching out via real world calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Information we need

;; This token has to belong to a user with access to the channel being polled.
;;
;; Generate your own token by creating a personal account token in the user
;; settings. Ask your admin to allow you to do this if you don't see an option.
(def token (System/getenv "ROBONONA_MATTERMOST_TOKEN"))

;; We assume https://, just pass the host
(def host "mattermost.internal.tulip.io")

;; Let's make it easier for configuration to use user-friendly team and channel
;; names rather than random IDs.
(def team "General")
(def channel "coffeebot-everywhere")

;; Set up base info for connecting to mattermost's API
(mattermost/set-api-context
 {:debug false
  :base-url (str "https://" host "/api/v4")
  :auths {"api_key" (str "Bearer " token)}})

;; I don't want these API calls running every single time I load the file,
;; because they will error out if I have no internet or haven't set my private
;; token. They aren't necessary to run either if I'm using this journal file to
;; develop and experiment with more ideas / features.
;;
;; The `comment` function requires all following forms to be valid clojure, but won't
;; run them. This allows you to verify their syntactical correctness but also you can
;; explicitly run them through your REPL or evaluator of choice.
;;
;; Apparently Rich Hickey uses these to experiment inside actual code files.
;; They're a good way of building up to a unit test and then eventually
;; extracting the code to its appropriate place.
;;
;; I don't know if it makes more sense to scatter my code like that or keep it
;; in this journal file. I guess I'll know better when I finally wind up
;; knowing what I am doing.
(comment
  ;; What is the channel name I care about?
  (def channel-id (->> channel
                       (channels/teams-name-team-name-channels-name-channel-name-get team)
                       :id))

  ;; My list of active users, returned in a paged way.
  ;; Note that I had to fork the mattermost api code to enable the `:active` param.
  ;; TODO Get my `:active` modification merged into the mainline code.
  (users/users-get {:page 0 :per-page 200 :active true :in-channel channel-id}))


;;;; 2022-07-16 - test to sketch out user matching API
;;;;
;;;; Leveraging TDD to guess at a code strcture that
;;;; splits out my pure functions from my impure
;;;; functions.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mattermost-component
  "Create a mattermost component"
  [host token]
  ;; Set up base info for connecting to mattermost's API
  {:context {:debug false
             :base-url (str "https://" host "/api/v4")
             :auths {"api_key" (str "Bearer " token)}}})

(defn get-active-users
  "Given `team` and `channel` names, return list of active users"
  [component team channel]
  '({} {} {} {}))

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  {:matches '((() ())
              (() ())
              (() ())
              (() ()))
   :unmatched '()})

(deftest pair-users
  (let [token (System/getenv "ROBONONA_MATTERMOST_TOKEN")
        host "mattermost.internal.tulip.io"
        team "General"
        channel "coffeebot-everwhere"
        mm (mattermost-component host token)
        users (get-active-users mm team channel)
        matches (match-users users)]
    (is (= (count matches) (/ (count users) 2)))))

(clojure.test/run-test pair-users)


;;;; 2022-07-17
;;;;
;;;; Can I use `clojure.spec` to design my core matching algorithm in a
;;;; consistent way before assuming any more details?
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; Here are my best guess of the data model I will start caring about.
;; Notice that I have break down each individual field into a spec so any part
;; of a data shape can be evaluated by `spec/conform` and `spec/valid?`
(spec/def ::id string?)
(spec/def ::username string?)
(spec/def ::user (spec/keys :req [::id ::username]))
(spec/def ::users (spec/coll-of ::user))

(spec/def ::matched-pair (spec/tuple ::user ::user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user (spec/nilable ::user))
(spec/def ::matches (spec/keys :req [::matched-pairs ::unmatched-user]))

;; This function definition spec says that the function's arguments (via `:arg`)
;; and return value (via `:ret`) have to confirm to particular specs.
;; The `:fn` replaces the traditional `:pre` and `:post` function definition keywords
;; to set a property-based invariant.
;;
;; An added bonus is that these become part of the function's documentation, so
;; you don't have to explicitly add documentation for arguments and return
;; values.
(spec/fdef match-users
  :args (spec/cat :users ::users)
  :ret ::matches
  :fn #(if (even? (count (-> % :args :users)))
         (nil? (-> % :ret ::unmatched-user))
         (not (nil? (-> % :ret ::unmatched-user)))))

(comment
  ;; Normally at runtime we don't validate these argument/return value/invarant
  ;; specs, for performance reasons.
  ;;
  ;; You may want to explicitly turn these on for api calls to force input checking;
  ;; `spec-test/instrument` can do this.
  (spec-test/instrument [match-users])
  ;; If you really wan this turned on for everything (in development mode
  ;; maybe? or when debugging some mystery) you can turn it on by passing no
  ;; arguments
  (spec-test/instrument))


;;;; 2022-07-17
;;;;
;;;; Practialli's development flow w/ TDD + spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Found at https://www.youtube.com/watch?v=mXNZxy71zT4&list=PLpr9V-R8ZxiDjyU7cQYWOEFBDR1t7t0wv&index=78
;; This is a really cool idea. Let's try it with what I have!

;;; 1. Write a failing test

(deftest pair-users
  (testing "even number of users"
    (let [users []
          result (match-users users)]
      (is (= (count (:matched-users result)) (/ (count users) 2)))
      (is (nil? (:unmatched-user result)))))
  (testing "odd number of users"
    (let [users []
          result (match-users users)]
      (is (= (count (:matched-users result)) (/ (dec (count users)) 2)))
      (is (not (nil? (:unmatched-user result)))))))

(clojure.test/run-test pair-users)

;;; 2. Write mock data for that test

(def even-mock-user-list
  [{:id "aaa" :username "a@test.com"}
   {:id "bbb" :username "b@test.com"}])

(def odd-mock-user-list
  [{:id "aaa" :username "a@test.com"}])

;;; 3. Write a function definition that returns the argument as passed in

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  users)

;;; 4. Tests will obviously fail

(deftest pair-users
  (testing "even number of users"
    (let [users even-mock-user-list
          result (match-users users)]
      (is (= (count (:matched-users result)) (/ (count users) 2)))
      (is (nil? (:unmatched-user result)))))
  (testing "odd number of users"
    (let [users odd-mock-user-list
          result (match-users users)]
      (is (= (count (:matched-users result)) (/ (dec (count users)) 2)))
      (is (not (nil? (:unmatched-user result)))))))

(clojure.test/run-test pair-users)

;;; 5. Write spec for function's argument

(spec/def ::id string?)
(spec/def ::username string?)
(spec/def ::user (spec/keys :req [::id ::username]))
(spec/def ::users (spec/coll-of ::user))

;;; 6. Write spec for return value

(spec/def ::matched-pair (spec/tuple ::user ::user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))

;;; 7. Replace mock data with generated values from specification.

;; Use `spec-gen/generate` to generate a single random conforming entity for the spec.
;; Use `spec-gen/sample` to generate a list of random conforming entities for the spec.
;; `spec-gen/exercise` generates values and conforms them to find edge cases.
;; For some reason these functions require the `test.check` library to be loaded.

(def even-mock-user-list
  [(spec-gen/generate (spec/gen ::user))
   (spec-gen/generate (spec/gen ::user))])
(def odd-mock-user-list
  [(spec-gen/generate (spec/gen ::user))])

(deftest pair-users
  (testing "even number of users"
    (let [users even-mock-user-list
          result (match-users users)]
      (is (= (count (::matched-pairs result)) (/ (count users) 2)))
      (is (not (contains? result ::unmatched-user)))
      (is (spec/valid? ::matches result))))
  (testing "odd number of users"
    (let [users odd-mock-user-list
          result (match-users users)]
      (is (= (count (::matched-pairs result)) (/ (dec (count users)) 2)))
      (is (contains? result ::unmatched-user))
      (is (spec/valid? ::matches result)))))

(clojure.test/run-test pair-users)

;;; 8. Update functions and make tests pass

(defn match-users
  "Group users into pairs. If odd number of users, return unmatched user."
  [users]
  (let [shuffled (shuffle users)]
    (if (even? (count shuffled))
      ;; For some reason we need vectors here to conform to `spec/tuple`
      {::matched-pairs (map vec (partition 2 shuffled))}
      {::matched-pairs (map vec (partition 2 (drop 1 shuffled)))
       ::unmatched-user (first shuffled)})))

;; NOTE: I ended up going back and fortch changing specs and implementation
;; based on feedback from failing tests.
;; `explain-data` and playing around with validating generated and manually
;; crafted data helped me figure this out.

;;; 9. Generate function specification based on unit test contracts

;; NOTE: Thinking about property-based testing helps, especially if you wind
;; up writing unit tests that way. The `:fn` argument you write should ideally
;; be way simpler than the function you used to generate your function's result

(spec/fdef match-users
  :args (spec/cat :users ::users)
  :ret ::matches
  ;; Length of pairs should be approximately half the length of candidae users;
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (spec/and #(= (quot (count (-> % :args :users)) 2)
               (count (-> % :ret ::matched-pairs)))
            #(if (even? (count (-> % :args :users)))
              (not (contains? (:ret  %) ::unmatched-user))
              (contains? (:ret %) ::unmatched-user))))

;; 9. Run specification checks

(spec-test/instrument `match-users)

;; Run this a few dozen times to see if you find a breaking example.
;; NOTE: It looks like only arguments are checked, not `:fn` or `:ret`

(match-users (spec-gen/generate (spec/gen ::users)))

(/ (count (spec-gen/generate (spec/gen ::users))) 2)

;; This is how you generate a thousand? inputs and actually evaluate the entire
;; functional spec.

(spec-test/check `match-users)

;; And we're done with this function!
