;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.matcher
  (:require [clojure.spec.alpha :as spec]
            [robot-disco.robonona.history :as history]
            [robot-disco.robonona.slack.protocol :as slack]))

;;; Coffeebot pairing specifications
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(spec/def ::user-id ::slack/user-id)
(spec/def ::matched-pair (spec/tuple ::user-id ::user-id))
(spec/def ::matched-pairs (spec/coll-of ::matched-pair))
(spec/def ::unmatched-user ::user-id)
(spec/def ::matches (spec/keys :req [::matched-pairs]
                               :opt [::unmatched-user]))

;;; Random Pairing Logic
;;;;;;;;;;;;;;;;;;;;;;;;

(defn- remove-ignored-users
  [users ignore]
  (let [ignore-ids (into #{} (map :user/id ignore))]
    (remove #(ignore-ids (:user/id %)) users)))

(defn random-match
  "Randomly pair users by shuffling and grouping into pairs.
   If odd number of users, one user is returned as ::unmatched-user."
  [users]
  (let [shuffled (shuffle users)]
    (if (even? (count shuffled))
      ;; For some reason we need vectors here to conform to `spec/tuple`
      {::matched-pairs (map vec (partition 2 shuffled))}
      {::matched-pairs (map vec (partition 2 (drop 1 shuffled)))
       ::unmatched-user (first shuffled)})))

(spec/fdef random-match
  :args (spec/cat :users (spec/coll-of ::user-id))
  :ret ::matches
  ;; We should only get an unmatched user if the input list has an odd length.
  :fn (fn [{:keys [args ret]}]
        (let [users (:users args)]
          (if (even? (count users))
            (not (contains? ret ::unmatched-user))
            (contains? ret ::unmatched-user)))))

;;; Round-Robin Pairing Logic
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- all-pairs
  "Generate all possible pairs from a collection of users."
  [users]
  (let [user-vec (vec users)]
    (for [i (range (count user-vec))
          j (range (inc i) (count user-vec))]
      [(nth user-vec i) (nth user-vec j)])))

(defn- calculate-pair-priority
  "Calculate priority for pairing two users.
   Higher priority = should be paired sooner.
   Never met = Long/MAX_VALUE
   Otherwise = milliseconds since last meeting."
  [history user-a user-b now]
  (let [pair-key (history/make-pair-key user-a user-b)
        last-met (get history pair-key)]
    (if last-met
      (- (.getTime now) (.getTime last-met))
      Long/MAX_VALUE)))

(defn round-robin-match
  "Match users using round-robin-like algorithm that prioritizes
   users who haven't met or met longest ago.

   Takes a collection of users and a history map (pair-key -> last-met timestamp).
   Returns same structure as random-match: ::matched-pairs and optionally ::unmatched-user."
  [users history]
  (if (< (count users) 2)
    ;; Handle edge cases: 0 or 1 users
    (if (seq users)
      {::matched-pairs []
       ::unmatched-user (first users)}
      {::matched-pairs []})
    ;; Normal case: 2+ users
    (let [now (java.util.Date.)
          user-vec (vec users)

          ;; Calculate priorities for all pairs
          pair-priorities (->> (all-pairs user-vec)
                               (map (fn [[a b]]
                                      {:pair [a b]
                                       :priority (calculate-pair-priority history a b now)}))
                               (sort-by :priority >))  ;; Highest priority first

          ;; Greedy matching
          result (loop [remaining (set user-vec)
                        priorities pair-priorities
                        matches []]
                   (if (< (count remaining) 2)
                     {:matches matches
                      :unmatched (first remaining)}
                     (let [;; Find best available pair
                           best (first (filter (fn [{:keys [pair]}]
                                                 (and (remaining (first pair))
                                                      (remaining (second pair))))
                                               priorities))]
                       (if best
                         (recur (disj remaining (first (:pair best)) (second (:pair best)))
                                priorities
                                (conj matches (vec (:pair best))))
                         ;; No valid pairs found (shouldn't happen with 2+ remaining)
                         {:matches matches
                          :unmatched (first remaining)}))))]

      (if (:unmatched result)
        {::matched-pairs (:matches result)
         ::unmatched-user (:unmatched result)}
        {::matched-pairs (:matches result)}))))

(spec/fdef round-robin-match
  :args (spec/cat :users (spec/coll-of ::user-id)
                  :history ::history/pairing-history)
  :ret ::matches
  :fn (fn [{:keys [args ret]}]
        (let [users (:users args)]
          (if (even? (count users))
            (not (contains? ret ::unmatched-user))
            (contains? ret ::unmatched-user)))))

