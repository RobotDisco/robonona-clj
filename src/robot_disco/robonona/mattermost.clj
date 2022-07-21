(ns robot-disco.robonona.mattermost
  (:require [clojure.set]
            [clojure.spec.alpha :as spec]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [robot-disco.robonona.mattermost.user :as-alias user]
            [robot-disco.robonona.mattermost.json :as-alias json-user]
            [robot-disco.robonona.mattermost.channel :as-alias channel]
            [robot-disco.robonona.mattermost.team :as-alias team]))


;;; Mattermost data specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data as returned to us from mattermost APIs.
;;
;;
(spec/def ::team/name string?)
(spec/def ::channel/name string?)
(spec/def ::channel/id string?)


(spec/def ::user/id string?)
(spec/def ::user/username string?)
(spec/def ::user/user (spec/keys :req [::user/id ::user/username]))
(spec/def ::user/users (spec/coll-of ::user/user))

(spec/def ::json-user/user (spec/keys :req-un [::user/id ::user/username]))


;;; Mattermost API results
;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Encoding responses from mattermost HTTP servers

(spec/def ::success boolean?)
(spec/def ::reason (spec/or :int int? :keyword keyword?))
(spec/def ::api-result (spec/keys :req [::success]
                                  :opt [::reason]))


;;; API Context
;;;;;;;;;;;;;;;
;;; Mattermost host and Auth Tokens don't usually get changed after being set, so let's
;;; put them in a context so we don't have to pass them as arguments all the time.
;;;
;;; This code was inspired by and/or lifted from
;;; [[mattermost-clj][https://github.com/devth/mattermost-clj]]

(spec/def ::base-url string?)
(spec/def ::auth-token string?)
(spec/def ::api-context (spec/keys :req [::base-url ::auth-token]))

(def default-api-context
  "Default API context."
  {::base-url "https://your-mattermost-url.com/api/v4"
   ::auth-token "please_set_me"})


(def ^:dynamic *api-context*
  "Dynamic API context to be applied in  API calls."
  default-api-context)


(defn set-api-context
  "Set the *api-context* globally"
  [new-context]
  (alter-var-root #'*api-context* (constantly
                                   (merge *api-context* new-context))))

(spec/fdef set-api-context
  :args (spec/cat :context ::api-context))


;;; Generic Mattermost HTTP request logic
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def request-page-limit
  "Maximum number of pages to request before returning the call as is.
  This is approximately 1000 entries if we assume a page size of
  [[max-items-per-page]] entries."
  16)

(def max-items-per-page
  "Number of items we expect to see on a full mattermost paginated response."
  60)

(def interval-between-requests
  "Number of milliseconds to sleep between page requests."
  1000)


;;; Data conversion
;;;;;;;;;;;;;;;;;;;
;; Convert JSON shapes to more powerful internal ones


(defn json-user->user
  "Convert mattermost's user structure info coffeebot's user structure."
  [mm-user]
  (clojure.set/rename-keys mm-user
                           {:id ::user/id
                            :username ::user/username}))

(spec/fdef json-user->user
  :args (spec/cat :mm-user ::json-user/user)
  :ret ::user/user
  :fn #(let [input (-> % :args :mm-user)
             output (-> % :ret)]
         (spec/and (= (:id input) (::user/id output))
                   (= (:username input) (::user/username output)))))


;;; Mattermost User calls
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn channel-id-by-team-name-and-channel-name
  [team-name channel-name]
  (let [url (str (::base-url *api-context*)
                 "/teams/name/"
                 team-name
                 "/channels/name/"
                 channel-name)
        result (http/get url
                         {:headers {"Authorization"
                                    (str "Bearer " (::auth-token *api-context*))}
                          :as :json})]
    (-> result :body :id)))

(spec/fdef channel-id-by-team-name-and-channel-name
  :args (spec/cat :team-name ::team/name
                  :channel-name ::channel/name)
  :ret ::channel/id)


(defn active-users-by-channel-id
  "Fetch list of active users from channel with ID `channel-id`.

  We also expect the mattermost `host` (which is assumed to be using HTTPS) and
  the auth bearer `token` (with is the personal access token of a user or a bot
  account.)

  Note that this call handles mattermost's paginated responses, with calls to
  [[Thread/sleep]] between each page to ensure we don't slam the server. We also
  will return at most `request-page-limit` pages' worth of data so that we don't
  freeze on channels with ludicriously large numbers of users in them."
  [channel-id]
  (loop [results []
         page 0]
    (let [url (str (::base-url *api-context*) "/users")
          query-params {"page" page
                        "per_page" max-items-per-page
                        "active" true
                        "in_channel" channel-id}
          response (:body (http/get url
                                    {:query-params query-params
                                     :headers {"Authorization" (str "Bearer "
                                                                    (::auth-token *api-context*))}
                                     :as :json}))
          processed (map json-user->user response)
          accumulated-results (into results processed)
          ;; Stop after 16 pages (~1000 users)
          ;; or if the next page had zero entries
          continue? (and (< page request-page-limit)
                         (= (count processed) max-items-per-page))]
      (if continue?
        (do
          ;; Sleep between page requests as to not overwhelm the
          ;; mattermost server.
          (Thread/sleep interval-between-requests)
          (recur accumulated-results
                 (inc page)))
        accumulated-results))))

(spec/fdef active-users-by-channel-id
  :args (spec/cat :channel-id string?)
  :ret ::user/users)


(defn message-users
  "Message the people in `users` with the provided `message`.

  Note: This will create a group chat with all the specified users as well as
  the account representing this program in mattermost."
  [users message]
  (let [response (http/post (str (::base-url *api-context*)
                                 "/channels/group")
                            {:headers
                             {"Authorization" (str "Bearer "
                                                   (::auth-token *api-context*))}
                             :body (json/generate-string (map ::user/id users))
                             :content-type :json
                             :as :json})
        status (:status response)
        channel-id (get-in response [:body :id])]
    (cond
      (not (= 201 status)) {::success false ::reason status}
      (not channel-id) {::success false ::reason :channel-id-missing}
      :else
      (let [response (http/post (str (::base-url *api-context*)
                                     "/posts")
                                {:headers
                                 {"Authorization" (str "Bearer "
                                                       (::auth-token *api-context*))}
                                 :content-type :json
                                 :body (json/generate-string
                                        {"channel_id" channel-id
                                         "message" message})})
            status (:status response)]
        (if (= 201 status)
          {::success true}
          {::success false ::reason status})))))

(spec/fdef message-users
  :args (spec/cat :users ::user/users :message string?)
  :ret ::api-result)


(defn message-user
  "As `user1`, Message `user2` with the provided `message`."
  [user1 user2 message]
  (let [response (http/post (str (::base-url *api-context*)
                                 "/channels/direct")
                            {:headers
                             {"Authorization" (str "Bearer "
                                                   (::auth-token *api-context*))}
                             :body (json/generate-string
                                    [(::user/id user1)
                                     (::user/id user2)])
                             :content-type :json
                             :as :json})
        status (:status response)
        channel-id (get-in response [:body :id])]
    (cond
      (not (= 201 status)) {::success false ::reason status}
      (not channel-id) {::success false ::reason :channel-id-missing}
      :else
      (let [response (http/post (str (::base-url *api-context*)
                                     "/posts")
                                {:headers
                                 {"Authorization" (str "Bearer "
                                                       (::auth-token *api-context*))}
                                 :content-type :json
                                 :body (json/generate-string {"channel_id" channel-id
                                                              "message" message})})
            status (:status response)]
        (if (= 201 status)
          {::success true}
          {::success false ::reason status})))))

(spec/fdef message-user
  :args (spec/cat :user1 ::user/user
                  :user2 ::user/user
                  :message string?)
  :ret ::api-result)


(defn get-my-info
  "Get user id associated with session token."
  []
  (let [url (str (::base-url *api-context*)
                 "/users/me")
        result (http/get url
                         {:headers {"Authorization" (str "Bearer "
                                                         (::auth-token *api-context*))}
                          :as :json})]
    (-> result :body json-user->user)))

(spec/fdef get-my-id
  :args (spec/cat)
  :ret ::user/user)

;;; Scratchpad
;;;;;;;;;;;;;;
(comment

  (require '[clojure.spec.gen.alpha :as spec-gen])) ;; Comment ends here

