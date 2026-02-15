;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.mattermost.http-client
  "HTTP implementation of the Mattermost client protocol."
  (:require [robot-disco.robonona.mattermost.protocol :as protocol]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

;;; Constants
;;;;;;;;;;;;;

(def ^:private request-page-limit
  "Maximum number of pages to request before returning."
  16)

(def ^:private max-items-per-page
  "Number of items per page in Mattermost paginated responses."
  60)

(def ^:private interval-between-requests
  "Milliseconds to sleep between page requests."
  1000)

;;; HTTP Client Record
;;;;;;;;;;;;;;;;;;;;;;

(defrecord HttpClient [base-url token team]
  protocol/Client

  (get-channel-users [_ channel-id]
    ;; Fetch users with pagination handling
    (loop [results []
           page 0]
      (let [url (str base-url "/users")
            query-params {:page page
                          :per_page max-items-per-page
                          :active true
                          :in_channel channel-id}
            response (http/get url
                               {:query-params query-params
                                :headers {:authorization (str "Bearer " token)}})
            body (json/parse-string (:body response) true)
            ;; Extract just the user IDs (protocol expects IDs, not full user objects)
            user-ids (map :id body)
            accumulated-results (into results user-ids)
            continue? (and (< page request-page-limit)
                           (= (count body) max-items-per-page))]
        (if continue?
          (do
            (Thread/sleep interval-between-requests)
            (recur accumulated-results (inc page)))
          accumulated-results))))

  (get-conversation-id [_ members]
    ;; Create a group conversation (or direct message for 2 users)
    (let [endpoint (if (= 2 (count members))
                     "/channels/direct"
                     "/channels/group")
          response (http/post (str base-url endpoint)
                              {:headers {:authorization (str "Bearer " token)
                                         :content-type "application/json"}
                               :body (json/generate-string members)})
          body (json/parse-string (:body response) true)]
      (:id body)))

  (post-message [_ channel-id text]
    (let [response (http/post (str base-url "/posts")
                              {:headers {:authorization (str "Bearer " token)
                                         :content-type "application/json"}
                               :body (json/generate-string {:channel_id channel-id
                                                            :message text})})
          body (json/parse-string (:body response) true)]
      (:id body))))

;;; Helper functions
;;;;;;;;;;;;;;;;;;;;

(defn channel-id-by-name
  "Look up channel ID by team and channel name.
   Use this to convert channel names to IDs before calling protocol methods."
  [client channel-name]
  (let [{:keys [base-url token team]} client
        url (str base-url "/teams/name/" team "/channels/name/" channel-name)
        response (http/get url
                           {:headers {:authorization (str "Bearer " token)}})
        body (json/parse-string (:body response) true)]
    (:id body)))
