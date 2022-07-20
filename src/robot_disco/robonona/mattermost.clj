(ns robot-disco.robonona.mattermost
  (:require [clojure.spec.alpha :as spec]
            [clj-http.client :as http]))


;;; Mattermost data specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data as returned to us from mattermost APIs.
;;
;; This probably should live in its own class, handling mattermost data feels
;; like a unit of responsibility

;; Thus far this is the same format for all entity id fields.
(spec/def ::id string?)

(spec/def ::username string?)
(spec/def ::user (spec/keys :req-un [::id ::username]))
(spec/def ::users (spec/coll-of ::user))


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


;;; Mattermost User calls
;;;;;;;;;;;;;;;;;;;;;;;;;


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
          accumulated-results (into results response)
          ;; Stop after 16 pages (~1000 users)
          ;; or if the next page had zero entries
          continue? (and (< page request-page-limit)
                         (= (count response) max-items-per-page))]
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
  :ret ::users)



;;; Scratchpad
;;;;;;;;;;;;;;
(comment

  (require '[clojure.spec.gen.alpha :as spec-gen])



  ) ;; Comment ends here

