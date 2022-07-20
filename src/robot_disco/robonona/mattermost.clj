(ns robot-disco.robonona.mattermost
  (:require [clojure.set]
            [clojure.spec.alpha :as spec]
            [clj-http.client :as http]
            [robot-disco.robonona.mattermost.user :as-alias user]
            [robot-disco.robonona.mattermost.json :as-alias json]
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

(spec/def ::json/user (spec/keys :req-un [::user/id ::user/username]))


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
  :args (spec/cat :mm-user ::json/user)
  :ret ::user/user
  :fn #(let [input (-> % :args :mm-user)
             output (-> % :ret)]
         (spec/and (= (:id input) (::user/id output))
                   (= (:username input) (::user/username output)))))


;;; Mattermost User calls
;;;;;;;;;;;;;;;;;;;;;;;;;


(defn channel-id-by-team-name-and-channel-name
  [host token team-name channel-name]
  (let [url (str "https://"
                 host
                 "/api/v4"
                 "/teams/name/"
                 team-name
                 "/channels/name/"
                 channel-name)
        result (http/get url
                         {:header {"Authorization" (str "Bearer " token)}
                          :as :json})]
    (-> result :body :id)))

(spec/fdef channel-id-by-team-name-and-channel-name
  :args (spec/cat :host string?
                  :token string?
                  :team-name ::team/name
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
  [host token channel-id]
  (loop [results []
         page 0]
    (let [url (str "https://" host "/api/v4" "/users")
          query-params {"page" page
                        "per_page" max-items-per-page
                        "active" true
                        "in-channel" channel-id}
          response (:body (http/get url
                                    {:query-params query-params
                                     :headers {"Authorization" (str "Bearer "
                                                                    token)}
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
  :args (spec/cat :host string? :token string? :channel-id string?)
  :ret (spec/coll-of ::user/user))


;;; Scratchpad
;;;;;;;;;;;;;;
(comment

  (require '[clojure.spec.gen.alpha :as spec-gen])



  ) ;; Comment ends here

