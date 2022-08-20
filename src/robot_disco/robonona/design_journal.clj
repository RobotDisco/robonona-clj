(ns robot-disco.robonona.design-journal
  (:require
   ;; Tests and specifications
   [clojure.test :refer [deftest is testing]]
   [clojure.spec.alpha :as spec]
   [clojure.test.check :as test-check]
   [clojure.spec.gen.alpha :as spec-gen]
   [clojure.spec.test.alpha :as spec-test]
   ;; Mattermost API client
   ;; [mattermost-clj.core :as mattermost]
   ;; [mattermost-clj.api.channels :as channels]
   ;; [mattermost-clj.api.users :as users]
   ;; HTTP client
   [clj-http.client :as http]
   [cheshire.core :as json]
   ;; Application logic
   [robot-disco.robonona.coffeebot :as coffee]
   [robot-disco.robonona.mattermost :as coffeemm]))

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


  
  ) ;; Comment ends here

(comment
;;; 2022-07-18 TODO Next steps (not my current issue)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Practicalli seems to design web services around `integrant` (for components)
  ;; and `aero` (for config). I know I like aero. Integrant seemed like the one
  ;; system I found as a compelling alternative to Stuart Sierra's `component`,

  ;; https://practical.li/clojure-web-services/repl-driven-development/integrant-repl/
  ;; https://www.pixelated-noise.com/blog/2022/04/28/integrant-and-aero/index.html

  ;; I know I'm going to have to move towards a component model anyway
  ;; for testing and separating out pure from impure functions, so.

  ) ;; Comment ends here

(comment
;;; 2022-07-19 Mattermost API as a component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; First we isolate the http call, pagination handling for generic data
  ;; Since we're using a mattermost library we have to be able to handle
  ;; any get call, at least. (Any HTTP request type, even better.)
  (def request-page-limit
    "Maximum number of pages to request before returning the call as is."
    16)

  (defn request-paginated-data
    [function fn-kwargs]
    (loop [results []
           page 0]
      (let [response (apply function
                            [(merge {:page page} fn-kwargs)])
            accumulated-results (into results response)
            ;; Stop after 16 pages (~1000 users)
            ;; or if the next page had zero entries
            continue? (and (< page request-page-limit)
                           (< 0 (count response)))]
        (if continue?
          (do
            (Thread/sleep 1000)
            (recur accumulated-results
                   (inc page)))
          accumulated-results))))

  (defn active-users-by-channel-id
    [channel-id]
    (request-paginated-data users/users-get
                            {:active true :in-channel channel-id}))

  (count (active-users-by-channel-id channel-id))
  (first (active-users-by-channel-id channel-id))

  ;; OK cool so we can isolate the paginated data request. Now, where is the
  ;; right place to handle the translation processing?

  (defn active-users-by-channel-id
    [channel-id]
    (let [response (request-paginated-data
                    users/users-get
                    {:active true :in-channel channel-id})]
      (map coffee/mattermost-user->user response)))

  (first (active-users-by-channel-id channel-id))

  ;; Do I really want to do this map over and over in every call? If I was doing
  ;; TDD first, then yes. I really want to move processing into
  ;; `request-paginated-data` intuitively, but that might be premature
  ;; optimization. Really at this point I should be thinking about TDDing http
  ;; calls.

  ;; Luckily the mattermost api library uses `clj-http`, which is easy to mock
  ;; via the recommended `clj-http-fake` library. Let's start TDDing a
  ;; mattermost side-effectful component.



  ) ;; Comment ends here

(comment
;;; 2022-07-19 `active-users-by-channel-id` via TDD
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Moving mattermost API specs into new `mattermost` namespace.

  ;; Revised TDD practice (based on practicalli website)
  ;; 1. Write test, don't both defining data just yet outside `is` statements.
  ;; 2. Model concrete instances data models you need, existing or new.
  ;;    Plug them into your test, which will still fail.
  ;; 3. Write a definition of your test where it just returns the input
  ;; 4. Write/extend specs for the input and output data models, if necessary.
  ;; 5. Verify that your fake model data conforms to said spec. Use generated
  ;;    spec data in test(replacing mocked atoms, extending mocked collections.)
  ;; 6. Implement function, make it pass, validate output's spec
  ;; 7. Write `fdef`, definitly `:ret` and `:arg`, ideally `:fn`
  ;; 8. Use `spec-test/instrument` with generated inputs to validate function
  ;; 9. If `:fn` was used, run `spec-test/check` to do property-based testing.
  ;; 10. Document functions
  ;; 11. Refactor if desired



  ) ;; Comment ends here

(comment
  ;;; 2022-07-19 A Snag: Abandoning `mattermost-clj`

  ;; Turns out I can't use `mattermost-clj` and `clj-http.fake` because of the
  ;; way `clojure.test` evals stuff. I'm going to have to write code that calls
  ;; `clj-http` directly.

  (def token (System/getenv "ROBONONA_MATTERMOST_TOKEN"))
  (def host "mattermost.internal.tulip.io")

  (def team "general")
  (def channel "coffeebot-everywhere")

  ;; Get channel ID
  
  (def channel-id (-> (http/get (str "https://" host
                                     "/api/v4"
                                     "/teams/name/"
                                     team
                                     "/channels/name/"
                                     channel)
                       {:query-params {"team_name" team
                                       "channel_name" channel}
                        :headers {"Authorization" (str "Bearer " token)}
                        :as :json})
                      :body
                      :id))

  ;; Get active users

  (:body (http/get (str "https://" host
                        "/api/v4"
                        "/users")
                   {:query-params {"page" 0
                                   "per_page" 60
                                   "active" true
                                   "in-channel" channel-id}
                    :headers {"Authorization" (str "Bearer " token)}
                    :as :json}))

  ;; OK, reimplement `active-users-by-channel-id` using this. Soft-restart the
  ;; process from the start since I need to pass in the token and host
  ;; and can't assume some context is being held for me with that info.
  ;;
  ;; Or fuck it, implement my own context? Maybe it's a good pattern? Those
  ;; values never change throughout the app lifecycle anyway...

  ;; Ended up deciding a context was too overkill for now. Maybe writing more
  ;; code will show me what patterns are useful...
  ;; Trying out `with-redefs` as *in theory* that it is the simplest form.
  ;;
  ;; Realized `clj-http-fake` forces you to pull your code into the test
  ;; namespace, which feels which feels janky. With the author could have said
  ;; that instead of being an unhelpful asshat.

  (http/get "http://www.google.com")
  (with-redefs [http/get (fn [_] {:body "Eat my shorts!"})]
    (http/get "http://www.google.com"))
  ;; => {:body "Eat my shorts!"}

  ;; The heck, why doesn't it work in my code...
  ;; Oh just needed to reload things...

  ;; I am sure this will wind up being annoying and I'll want a better fake/mock
  ;; as my calls get more complex...



  )  ;; Comment ends here

(comment
  ;;; 2022-07-20 Refactoring
  ;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Tried to move specs into centralized place, didn't like the feel of it.

  ;; Refactored specs to have the `mattermost` namespace have the concept of
  ;; JSON and app specs to handle conversion between qualified and nonqualified
  ;; keyspaces. This allows me to reuse the specs and not feel like I have extra
  ;; specs that are basically the same across namespaces.
  ;;
  ;; Found out about the `:as-alias` namespace in the [[ns]] form which allows
  ;; me to qualify my specs even further without really creating new namespaces
  ;; I can see myself feeling that these are just predicting future namespaces,
  ;; however...
  ;;
  ;; Set up CIDER to skip generative tests, but those are in the test suite now
  ;; Plus instrument functions I care about when testing
  ;;
  ;; I can reuse that fixture when developing I imagine.


  )  ;; Comment ends here

(comment
  ;;; 2022-07-20 Get channel by team and channel name
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (def team "general")
  (def channel "coffeebot-everywhere")

  (def token (System/getenv "ROBONONA_MATTERMOST_TOKEN"))
  (def host "mattermost.internal.tulip.io")
  
  ;; Get channel ID
  
  (http/get (str "https://" host
                 "/api/v4"
                 "/teams/name/"
                 team
                 "/channels/name/"
                 channel)
            {:query-params {"team_name" team
                            "channel_name" channel}
             :headers {"Authorization" (str "Bearer " token)}
             :as :json})
  ;; => {:request-time 172,
  ;;     :repeatable? false,
  ;;     :protocol-version {:name "HTTP", :major 1, :minor 1},
  ;;     :streaming? true,
  ;;     :chunked? false,
  ;;     :reason-phrase "OK",
  ;;     :headers
  ;;     {"Server" "nginx",
  ;;      "X-Version-Id" "5.37.0.5.37.0.382b8f8ad6d3fff622a71fe7f16f7a5d.true",
  ;;      "Content-Type" "application/json",
  ;;      "Content-Length" "740",
  ;;      "Connection" "close",
  ;;      "Expires" "0",
  ;;      "Date" "Wed, 20 Jul 2022 22:07:45 GMT",
  ;;      "Vary" "Accept-Encoding",
  ;;      "X-Request-Id" "fbyebdxzqjgsjcusyq65d5opua"},
  ;;     :orig-content-encoding nil,
  ;;     :status 200,
  ;;     :length 740,
  ;;     :body
  ;;     {:total_msg_count 105,
  ;;      :total_msg_count_root 312,
  ;;      :purpose
  ;;      "Have a coffeecat with potentially any tulip employee located aNyWhErE",
  ;;      :extra_update_at 0,
  ;;      :name "coffeebot-everywhere",
  ;;      :update_at 1585320629807,
  ;;      :policy_id nil,
  ;;      :type "O",
  ;;      :header
  ;;      "Every Tuesday morning, get paired up with people at tulip to videochat over coffee! or chats! Or expanding social horizons!\n\nFor features/issues: https://github.com/RobotDisco/mattermost-bagel/issues",
  ;;      :creator_id "iuqt7hgyypfsdjr1nfi96u9wwe",
  ;;      :last_post_at 1657750370457,
  ;;      :scheme_id nil,
  ;;      :group_constrained nil,
  ;;      :id "xu5t8og6o3yfmbj3bxbymy4wqa",
  ;;      :delete_at 0,
  ;;      :shared nil,
  ;;      :team_id "uqbg8wcabtdb7k6hwu7syd1eqc",
  ;;      :display_name "coffeebot-everywhere",
  ;;      :create_at 1584705627458,
  ;;      :props nil},
  ;;     :trace-redirects []}
  


  )  ;; Comment ends here

(comment
  ;;; 2022-07-20 Message people who are (un)matched
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Hmm. How do I test a side-effect?
  ;; How do I red-green-refactor a side effect?

  ;; Can I test around the side effect and care only about its input and output?
  ;; Reading up on things, it seems like a lot of people feel the side-effects
  ;; don't matter?

  ;; There are frameworks like `spy`. Except maybe I don't care other than being
  ;; able to control redefining key functions like [[http/get]] with
  ;; [[with-redefs]] for faking.

  ;; There are probably times I care about whether a particular outside function
  ;; are called or how many times it is called.
  ;; Right now I am not sure if I am in any of those times.

  ;; Unrelated
  ;; I played around with a macro called `better-cond`. It supposedly makes
  ;; nested conditionals look a lot nicer.
  ;;
  ;; While that is true, emacs' syntax checking can't handle it. Sad.

  ;; clj-http doesn't auto-coerce input into json. It will coerce output with
  ;; the `:as` keyword.

  ) ;; Comment ends here


(comment
  ;;; 2022-07-20 A snag: I need to know my own ID
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;;; Mattermost won't let me message folk unless I know my own ID.

  ;;; I can fetch it via `/api/v4/users/me` and my token so it's not too hard
  ;;; to get.



  ) ;; Comment ends here

(comment
  ;;; 2022-07-21 Putting it all together
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;;; My run function looks really nice with everything split out

  ;;; Bots need to be explicitly invited into teams and channels. I didn't
  ;;; win anything there.

  ;;; I don't have to log into anything. I can just use my bearer key for my
  ;;; purposes.

  ;;; keywords have to be namespaced when using destructing inside maps.
  ;;; e.g. `{::user/keys [id username]}`

  ;;; Woah you can use a map directly after the optional rest indicator to enable
  ;;; keyword arguments!



  ) ;; Comments end here

(comment
  ;;; 2022-07-21 TODO Future Ideas
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; I still think there's too much coupling between business logic and
  ;; mattermost logic. There should probably be a main function, that references
  ;; both modules, that configures mattermost functionality and then runs the
  ;; business logic.

  ;; Additionally, I dislike how the business logic calls mattermost
  ;; functionality, even if it is split into two different modules. It should be
  ;; more split; the business logic should know _nothing_ about mattermost.
  ;;
  ;; Can I do this by emitting messages for a mattermost module that then
  ;; actually does the call. Would that be event dispatching? Do I need a third
  ;; module?

  ;; I also need to make sure I remove the bot from consideration in matches.
  ;; Unfortunately because of how I get active users, it is included in the
  ;; fetch call.



  )  ;; Comment ends here.

(comment
  ;;;; 2022-08-09 Bot shouldn't match itself
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Realized that currently the bot could match users with itself. Disallowed
  ;; that.

  ;; My tests don't explicitly handle the assumptions I made when implementing
  ;; this, so I had to rely on manual "integration" tests. Is this a sign I need
  ;; to write an integration test that legit relies on mattermost? Maybe. I
  ;; don't know how I can cover that test entirely via faked payloads.

  ;; Also my tests don't instrument enough of the code, not enough spec tests
  ;; are being checked. Also, how do I set up a dev env where all my functions
  ;; are specc'd in a dev env?


  )  ;; Comment ends here.

(comment
  ;;;; 2022-08-09 Creating dev helpers
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; There are a bunch of things I do all the time, like mark functions to
  ;; instrument, or run the "end-to-end" coffeebot functionality. There's a
  ;; pattern for setting these up as helper functions (as well as pre-loading
  ;; the namespaces I most engge with) where we do this in a `dev/user.clj`
  ;; that is only loaded with a special alias.

  ;; Practiclli's basic guide on this is at https://practical.li/clojure-staging/clojure-tools/projects/configure-repl-startup.html
  ;; The Clojure website itself goes into more depth, and even suggests eventual
  ;; third-party industrial-strength tooling: https://clojure.org/guides/repl/introduction


  )  ;; Comment ends here.

(comment
  ;;;; 2022-08-10 Run dry/integration tests from dev helper
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; Have to think of different entrypoints for testing
  ;; dry run and dev integration: Do it in REPL
  ;; run via commandline

  ;; Only did the REPL functions to start. Using juxt/aero because I don't think
  ;; we should pass credentials via arguments. But we'll need a main function
  ;; that can run from the commandline. Possibly dry-run is a valid param?
  ;; Can I merge juxt/aero with babashka-cli?
  ;; Can I add more testing here and possibly spec?


  )  ;; Comment ends here.

(comment
  ;;;; 2022-08-19 Run as a `clj` command
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; `clj -M` leverages a -main function in a compiled function.
  ;; This uses positional string arguments that needs to be parsed explicitly
  ;; `clj -X` can use an arbitrary function with a map for arguments
  ;; `clj -T` is used to run a tool that doesn't need our project's classpath.

  ;; It feels like Cognitect prefers -X because it doesn't require parsing
  ;; args or compiling.
  ;; I feel like I will wind up using -M because clj-nix' derivations leverage
  ;; it. Also if I want a ahead-of-time-compiled binary I need to use it I
  ;; think? Maybe just because of `clj-nix`

  ;; `juxt/aero` really wants a single config file containing my environment
  ;; values. I think this means that it isn't great for allowing others to use
  ;; this without a lot of complex config merging. Maybe not a problem for now.


  )  ;; Comment ends here.

(comment
  ;;;; 2022-08-19 Convert to use -M form
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; For the best as I want to compile this into an uberjar for containers
  ;; anyway.

  ;; Oh if I append my args to the `clj -M:<alias>` invocation they get picked
  ;; up.



  )  ;; Comment ends here.
