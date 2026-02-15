;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.history
  "Pairing history tracking for round-robin matching."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [robot-disco.robonona.slack.protocol :as slack]))

;;; Specs
;;;;;;;;;

;; Canonical pair key - always sorted for consistency
(spec/def ::pair-key (spec/and (spec/tuple ::slack/user-id ::slack/user-id)
                               #(neg? (compare (first %) (second %)))))

;; Timestamp of when the pair last met
(spec/def ::last-met inst?)

;; Map from pair-key to last-met timestamp
(spec/def ::pairing-history (spec/map-of ::pair-key ::last-met))

;; Last time the matcher ran
(spec/def ::last-run inst?)

;; Complete persisted state
(spec/def ::state (spec/keys :opt [::pairing-history ::last-run]))

;;; Helper functions
;;;;;;;;;;;;;;;;;;;;

(defn make-pair-key
  "Create a canonical pair key from two user IDs.
   Always returns the pair sorted alphabetically for consistency."
  [user-a user-b]
  (vec (sort [user-a user-b])))

(spec/fdef make-pair-key
  :args (spec/cat :user-a ::slack/user-id :user-b ::slack/user-id)
  :ret ::pair-key)

(defn prune-history
  "Remove history entries for users no longer in the active set.
   Call periodically to prevent unbounded growth."
  [history active-users]
  (let [active-set (set active-users)]
    (into {}
          (filter (fn [[[u1 u2] _]]
                    (and (active-set u1) (active-set u2)))
                  history))))

(spec/fdef prune-history
  :args (spec/cat :history ::pairing-history
                  :active-users (spec/coll-of ::slack/user-id))
  :ret ::pairing-history)

(defn update-history
  "Update history with new pairings at the given timestamp.
   Normalizes pair order to canonical form."
  [history pairs now]
  (reduce (fn [h [u1 u2]]
            (assoc h (make-pair-key u1 u2) now))
          history
          pairs))

(spec/fdef update-history
  :args (spec/cat :history ::pairing-history
                  :pairs (spec/coll-of (spec/tuple ::slack/user-id ::slack/user-id))
                  :now inst?)
  :ret ::pairing-history)

;;; I/O functions
;;;;;;;;;;;;;;;;;

(defn read-history
  "Read pairing history from EDN file.
   Returns empty state if file doesn't exist."
  [file-path]
  (if (.exists (io/file file-path))
    (edn/read-string (slurp file-path))
    {::pairing-history {}}))

(spec/fdef read-history
  :args (spec/cat :file-path string?)
  :ret ::state)

(defn write-history
  "Write pairing history state to EDN file."
  [file-path state]
  (spit file-path (pr-str state)))

(spec/fdef write-history
  :args (spec/cat :file-path string? :state ::state)
  :ret nil?)

;;; REPL-driven development
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; Test round-robin matching with history persistence

  (require '[robot-disco.robonona.matcher :as match])

  (def users ["U1" "U2" "U3" "U4" "U5" "U6"])
  (def history-file "/tmp/test-history.edn")

  ;; First run - empty history, pairs are essentially random
  (def state1 (read-history history-file))
  (def result1 (match/round-robin-match users (::pairing-history state1)))
  result1
  ;; => {:robot-disco.robonona.matcher/matched-pairs [["U1" "U4"] ...]}

  ;; Save the history
  (write-history history-file
                 {::pairing-history
                  (update-history {}
                                  (:robot-disco.robonona.matcher/matched-pairs result1)
                                  (java.util.Date.))
                  ::last-run (java.util.Date.)})

  ;; Check what was saved
  (slurp history-file)

  ;; Second run - should avoid previous pairs
  (def state2 (read-history history-file))
  (def result2 (match/round-robin-match users (::pairing-history state2)))
  result2
  ;; Different pairs this time!

  ;; Save again
  (write-history history-file
                 {::pairing-history
                  (update-history (::pairing-history state2)
                                  (:robot-disco.robonona.matcher/matched-pairs result2)
                                  (java.util.Date.))
                  ::last-run (java.util.Date.)})

  ;; Third run - continues avoiding recent pairs
  (def state3 (read-history history-file))
  (def result3 (match/round-robin-match users (::pairing-history state3)))
  result3

  ;; Clean up
  (.delete (java.io.File. history-file))

  ;; Test with odd number of users
  (def odd-users ["U1" "U2" "U3" "U4" "U5"])
  (match/round-robin-match odd-users {})
  ;; => {:robot-disco.robonona.matcher/matched-pairs [...],
  ;;     :robot-disco.robonona.matcher/unmatched-user "U?"}

  ;; Test pruning stale history
  (def old-history {["U1" "U2"] #inst "2026-01-01"
                    ["U1" "U3"] #inst "2026-01-08"
                    ["U2" "U3"] #inst "2026-01-15"})
  (def active-users #{"U1" "U2"})  ;; U3 left the channel
  (prune-history old-history active-users)
  ;; => {["U1" "U2"] #inst "2026-01-01"}
  )
