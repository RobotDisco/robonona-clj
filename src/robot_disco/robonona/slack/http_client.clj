;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.slack.http-client
  (:require [robot-disco.robonona.slack.protocol :as protocol]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defrecord HttpClient [token]
  protocol/Client
  (get-channel-users [_ channel-id]
    (loop [cursor nil
           users []]
      (let [response (http/get "https://slack.com/api/conversations.members"
                               {:headers {:authorization (str "Bearer "
                                                              token)}
                                :query-params {:channel channel-id
                                               :cursor cursor}})
            json (json/parse-string (:body response) true)
            body (protocol/validate-response json)
            user-ids (get body :members)
            next-cursor (get-in body [:response_metadata :next_cursor])]
        (if (str/blank? next-cursor)
          (into users user-ids)
          (recur next-cursor (into users user-ids))))))
  (get-conversation-id [_ user-ids]
    (let [response (http/post "https://slack.com/api/conversations.open"
                              {:headers {:authorization (str "Bearer " token)}
                               :query-params {:users (str/join "," user-ids)}})
          json (json/parse-string (:body response) true)
          body (protocol/validate-response json)]
      (get-in body [:channel :id])))
  (post-message [_ channel-id text]
    (let [response (http/post "https://slack.com/api/chat.postMessage"
                              {:headers {:authorization (str "Bearer " token)}
                               :query-params {:channel channel-id
                                              :text text}})
          json (json/parse-string (:body response) true)
          body (protocol/validate-response json)]
      (:ok body))))
