(ns robot-disco.robonona.design-journal
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
   [mattermost-clj.api.users :as users]
   ;; Application logic
   [robot-disco.robonona.coffeebot :as coffee]))

;;; I don't want these exploratory expressions running every single time I load
;;; the file; they might do side-effectful things with the systems I am
;;; experimenting with, or contain embryonic forms of tests that should not be
;;; expected to pass anymore, and generally contains code that should not be
;;; loaded in production use.
;;;
;;; The `comment` function requires all following forms to be valid clojure,
;;; but won't run them. This allows you to verify their syntactical correctness
;;; but also you can explicitly run them through your evaluator of choice.
;;;
;;; Apparently Rich Hickey uses these to experiment inside actual code files.
;;; They're a good way of building up to a unit test and then eventually
;;; extracting the code to its appropriate place.
;;; See `Rich Comment Forms` on google
;;;
;;; I don't know if it makes more sense to scatter my code like that or keep it
;;; in this journal file. I guess I'll know better when I finally wind up
;;; knowing what I am doing.

(comment 
;;; 2022-07-16 - Sketching out via real world calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

  
  ;; What is the channel name I care about?
  (def channel-id (->> channel
                       (channels/teams-name-team-name-channels-name-channel-name-get team)
                       :id))

  ;; My list of active users, returned in a paged way.
  ;; Note that I had to fork the mattermost api code to enable the `:active` param.
  ;; TODO Get my `:active` modification merged into the mainline code.
  (first (users/users-get {:page 0 :per-page 200 :active true :in-channel channel-id}))



  ) ;; Comment ends here

(comment
;;; 2022-07-16 - test to sketch out user matching API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Leveraging TDD to guess at a code strcture that
;;; splits out my pure functions from my impure
;;; functions.

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



  ) ;; Comment ends here

(comment 
;;; 2022-07-17
;;;;;;;;;;;;;;
;;; Can I use `clojure.spec` to design my core matching algorithm in a consistent
;;; way before assuming any more details?



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

  ;; This function definition spec says that the function's arguments (via
  ;; `:arg`) and return value (via `:ret`) have to confirm to particular specs.
  ;; The `:fn` replaces the traditional `:pre` and `:post` function definition
  ;; keywords to set a property-based invariant.
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

  
  ;; Normally at runtime we don't validate these argument/return value/invarant
  ;; specs, for performance reasons.
  ;;
  ;; You may want to explicitly turn these on for api calls to force input
  ;; checking;
  ;; `spec-test/instrument` can do this.
  (spec-test/instrument [match-users])



  ) ;; Comment ends here

(comment 
;;; 2022-07-17 Practialli's development flow w/ TDD + spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; ;; Found at https://www.youtube.com/watch?v=mXNZxy71zT4&list=PLpr9V-R8ZxiDjyU7cQYWOEFBDR1t7t0wv&index=78

  ;; 1. Write a failing test
  ;; 2. Write mock data for that test
  ;; 3. Write a function definition that returns the argument as passed in
  ;; 4. Tests will obviously fail
  ;; 5. Write spec for function's argument  
  ;; 6. Write spec for return value
  ;; 7. Replace mock data with generated values from specification.
  ;; 8. Update functions and make tests pass
  ;; 9. Generate function specification based on unit test contracts
  ;; 10. Run specification checks  

;;; This is a really cool idea. Let's try it with what I have!

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

  ;; Use `spec-gen/generate` to generate a single random conforming entity for
  ;; the spec.
  ;; Use `spec-gen/sample` to generate a list of random conforming entities for
  ;; the spec.
  ;; `spec-gen/exercise` generates values and conforms them to find edge cases.
  ;; For some reason these functions require the `test.check` library to be
  ;; loaded.

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

  ;; 10. Run specification checks

  (spec-test/instrument `match-users)

  ;; Run this a few dozen times to see if you find a breaking example.
  ;; NOTE: It looks like only arguments are checked, not `:fn` or `:ret`

  (match-users (spec-gen/generate (spec/gen ::users)))

  (/ (count (spec-gen/generate (spec/gen ::users))) 2)

  ;; This is how you generate a thousand? inputs and actually evaluate the entire
  ;; functional spec.

  (spec-test/check `match-users)

  ;; And we're done with this function!



  ) ;; Comment ends here

(comment
;;; 2022-07-17 Copied work into real file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Work from here is now distributed to
;; - coffeebot_spec.cljc
;; - coffeebot_test.clj
;; - coffeebot.clj

;; Is it worth having this file? Could I be working on those files directly
;; using "Rich Comment Forms", i.e. all my scratch work is at the bottom of
;; the real production code vs this journal?

;; It will note that at least with dated ordered transcripts it is easier to
;; find out what is relevant and how this whole thing evolved.



  ) ;; Comment ends here

(comment
;;; 2022-07-18 Translate mattermost user object into structure I care about
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; What does a user look like in MM?
  (first (users/users-get {:page 0 :per-page 200 :active true :in-channel channel-id}))
  ;; Example user JSON from mattermost
  ;; => {:email "gaelan.dcosta@test.com",
  ;;     :first_name "Gaelan",
  ;;     :timezone
  ;;     {:automaticTimezone "", :manualTimezone "", :useAutomaticTimezone "true"},
  ;;     :disable_welcome_email false,
  ;;     :locale "en",
  ;;     :last_picture_update 1628099675347,
  ;;     :update_at 1656426655948,
  ;;     :roles "system_user",
  ;;     :nickname "Gaelan",
  ;;     :auth_service "",
  ;;     :username "gaelan.dcosta",
  ;;     :auth_data "",
  ;;     :id "3881xw1gk78t9fsrxkadcuex7c",
  ;;     :delete_at 0,
  ;;     :last_name "D'costa",
  ;;     :position "Senior Software Developer",
  ;;     :create_at 1558451555923,
  ;;     :props
  ;;     {:customStatus
  ;;      "{\"emoji\":\"banana_dance\",\"text\":\"Peanut Butter \\u0026 Jelly Time\",\"duration\":\"today\",\"expires_at\":\"2022-06-29T03:59:59.999Z\"}"}}

  ;; Ended up refactoring entire codebase to put specs with code.
  ;; No clear guidance from cognitect.
  ;; Doing so makes data qualified namespaces more obvious however.

  ;; Means code can't work with Clojure < 1.9, I am ok with that here.

  ;; According to stack overflow there are differences between specs for testing
  ;; and specs for data / conforming? Don't yet understand the subtlety.

  ;; Recommendations for public-facing data is that they contain "organization"
  ;; and "project" namespace segments.
  ;; Recommendations for private projects, like this one, is something smaller
  ;; like `:coffeebot/users`. A nice thing is to leverage `::` tho since then I
  ;; can just move them over without renaming.

  ;; Should I generative test as part of unit tests?
  ;; Unsure. Will have to base on when test suit grows too big.
  ;; Generative tests might have to become their own pass if they block
  ;; fast feedback.

  ;; Apparently in spec I can't do `(spec/def ::user/id ...)` that is not a
  ;; valid keyword?



  ) ;; Comment ends here

(comment
;;; 2022-07-18 Convert list of mattermost users into matched pairs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  #_ (spec-test/instrument `coffee/match-users)
  #_ (spec-test/unstrument `coffee/match-users)
  
  (def matches (coffee/match-users
                (spec-gen/generate
                 (spec/gen
                  (spec/coll-of ::coffee/mattermost-user)))))

  (spec/valid? ::coffee/matches matches)
  ;; => false
  (spec/explain ::coffee/matches matches)


  ;; Hmm. When I instrument my match-users function I notice that the resulting
  ;; value does not conform because the keys are from `::mattermost-user`
  ;; Does this mean `::user` is unnecessary? Maybe. Let's defer until I do
  ;; enough to be annoyed by it.
  ;; TODO decide if a distinction between mattermost and coffeebot data for
  ;; users is worth it.



  ) ;; Comment ends here

(comment
;;; 2022-07-18 Fetch list of mattermost users from mattermost
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; How do I do this via components and event dispatch data?

  ;; My list of active users, returned in a paged way.
  ;; Note that I had to fork the mattermost api code to enable the `:active` param.


  (def token (System/getenv "ROBONONA_MATTERMOST_TOKEN"))
  (def host "mattermost.internal.tulip.io")

  ;; Impure function to set connection params
  (defn init
    [host token]
      (mattermost/set-api-context
   {:debug false
    :base-url (str "https://" host "/api/v4")
    :auths {"api_key" (str "Bearer " token)}}))

  (init host token)

  (def team "General")
  (def channel "coffeebot-everywhere")

  (defn channel-id-by-team-name-and-channel-name
    [team channel]
    (->> channel
         (channels/teams-name-team-name-channels-name-channel-name-get team)
         :id))

  (def channel-id (channel-id-by-team-name-and-channel-name team channel))

  ;; This returns only a single page of data
  (defn active-users-by-channel-id-one-page
    [channel-id]  
    (users/users-get {:page 0
                      :active true
                      :in-channel channel-id}))
 
  (first (active-users-by-channel-id-one-page channel-id))
  ;; => ({:email "fake.user@tulip.com",
  ;;      :first_name "Fake",
  ;;      :timezone
  ;;      {:automaticTimezone "", :manualTimezone "", :useAutomaticTimezone "true"},
  ;;      :disable_welcome_email false,
  ;;      :locale "en",
  ;;      :last_picture_update 1628099675347,
  ;;      :update_at 1656426655948,
  ;;      :roles "system_user",
  ;;      :nickname "Fake",
  ;;      :auth_service "",
  ;;      :username "fake.user",
  ;;      :auth_data "",
  ;;      :id "3881xw1gk78t9fsrxkadcuex7c",
  ;;      :delete_at 0,
  ;;      :last_name "User",
  ;;      :position "Senior Software Developer",
  ;;      :create_at 1558451555923,
  ;;      :props
  ;;      {:customStatus
  ;;       "{\"emoji\":\"banana_dance\",\"text\":\"Peanut Butter \\u0026 Jelly Time\",\"duration\":\"today\",\"expires_at\":\"2022-06-29T03:59:59.999Z\"}"}})

  ;; This requests all the pages until pages are empty and returns the full list

  (first (active-users-by-channel-id-one-page channel-id))

  (defn active-users-by-channel-id
    [channel-id]
    (loop [results []
           page 0]
      (let [body (users/users-get
                  {:page page :active true :in-channel channel-id})
            processed (map coffee/mattermost-user->user body)
            accumulated-results (into results processed)
            ;; Stop after 16 pages (~ 1000 users)
            ;; or if the next page had zero entries
            continue? (and (< page 16)
                           (< 0 (count processed)))]
        (if continue?
          (do
            (Thread/sleep 1000)
            (recur accumulated-results
                   (inc page)))
          accumulated-results))))

  (count (active-users-by-channel-id channel-id))
  (first (active-users-by-channel-id channel-id))

  ;; Note: We added a max page limit because if for some reason we are in a
  ;; channel with millions of users I don't want to take down the mattermost
  ;; host.
  ;; TODO How do I notify the user properly when the response has too many users
  ;; and we give up?
  ;; Note we also pause for a second between iterations as to not slam the
  ;; server.
  ;; TODO Make the pause between calls clearer
  ;; TODO Is it worth making these calls async? Probably not. Useful in other
  ;; contexts however.

  ;; Hmm even with my translator method `mattermost-user->user` all it does is
  ;; rename specific keys. It doesnt filter anything out. You know what, let's
  ;; just roll with it.

  ;; TODO How would I TDD this? How would I spec this? Can I leverage `spec`?

  ;; I think my next step is figure out how to make this testable
  ;; https://www.reddit.com/r/Clojure/comments/9vezcb/comment/e9cjl19/ seems
  ;; like the best resource

  ;; Start thinking about splitting the smallest piece of my above
  ;; `active-users-by-channel-id` out as the piece with the side effect
  ;; (the actual http call? the function that calls the mattermost library and
  ;; no more?) and that can't be tested, only mocked / stubbed.


  
  ) ;; Commend ends here

(comment
;;; 2022-07-18 Next steps (not my current issue)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Practicalli seems to design web services around `integrant` (for components)
  ;; and `aero` (for config). I know I like aero. Integrant seemed like the one
  ;; system I found as a compelling alternative to Stuart Sierra's `component`,

  ;; https://practical.li/clojure-web-services/repl-driven-development/integrant-repl/

  ;; I know I'm going to have to move towards a component model anyway
  ;; for testing and separating out pure from impure functions, so.

  ) ;; Comment ends here
