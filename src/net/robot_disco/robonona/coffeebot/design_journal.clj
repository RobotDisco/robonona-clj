(ns net.robot-disco.robonona.coffeebot.design-journal
  (:require
   ;; Tests and specifications
   [clojure.test :refer [deftest is testing]]
   [clojure.spec.alpha :as spec]
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

;; What is the channel name I care about?
(def channel-id (->> channel
                     (channels/teams-name-team-name-channels-name-channel-name-get team)
                     :id))

;; My list of active users, returned in a paged way.
;; Note that I had to fork the mattermost api code to enable the `:active` param.
;; TODO Get my `:active` modification merged into the mainline code.
(users/users-get {:page 0 :per-page 200 :active true :in-channel channel-id})


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

(spec/def ::id string?)
(spec/def ::username string?)
(spec/def ::user (spec/keys :req [::id ::username]))
(spec/def ::users (spec/coll-of ::user))

(spec/def ::matched-pair (spec/tuple ::user ::user))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user (spec/nilable ::user))
(spec/def ::matches (spec/keys :req [::matched-pairs ::unmatched-user]))
