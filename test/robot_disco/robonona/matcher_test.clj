;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.matcher-test
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.test.alpha :as spec-test]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [robot-disco.robonona.matcher :as SUT]))

;;; Functions to instrument
;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Turn these on when developing or troubleshooting

(defn instrumentation-fixture [f]
  (spec-test/instrument)
  (f)
  (spec-test/unstrument))

(use-fixtures :once instrumentation-fixture)
;;; Unit tests
;;;;;;;;;;;;;;

(deftest random-match-test
  (testing "even number of users"
    (let [users ["U1" "U2" "U3" "U4"]
          result (SUT/random-match users)]
      (is (= (count (::SUT/matched-pairs result)) (/ (count users) 2)))
      (is (not (contains? result ::SUT/unmatched-user)))
      (is (spec/valid? ::SUT/matches result))))
  (testing "odd number of users"
    (let [users ["U1" "U2" "U3"]
          result (SUT/random-match users)]
      (is (= (count (::SUT/matched-pairs result)) (/ (dec (count users)) 2)))
      (is (contains? result ::SUT/unmatched-user))
      (is (spec/valid? ::SUT/matches result)))))

(deftest ^:generative random-match-generative
  (is (every? (comp :pass? :clojure.spec.test.check/ret)
              (spec-test/check `SUT/random-match))))

;;; Round-robin matching tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest round-robin-match-test
  (testing "prioritizes users who have never met"
    (let [users ["U1" "U2" "U3" "U4"]
          ;; U1 and U2 met recently
          history {["U1" "U2"] #inst "2026-02-12"}
          result (SUT/round-robin-match users history)]
      ;; U1 and U2 should NOT be paired (they just met)
      (is (not (some #(= (set %) #{"U1" "U2"})
                     (map set (::SUT/matched-pairs result)))))
      (is (spec/valid? ::SUT/matches result))))

  (testing "pairs users who met longest ago when all have met"
    (let [users ["U1" "U2" "U3" "U4"]
          ;; Complete history - everyone has met
          history {["U1" "U2"] #inst "2026-02-12"  ;; Most recent
                   ["U1" "U3"] #inst "2026-01-01"  ;; Oldest
                   ["U1" "U4"] #inst "2026-02-01"
                   ["U2" "U3"] #inst "2026-02-05"
                   ["U2" "U4"] #inst "2026-01-15"
                   ["U3" "U4"] #inst "2026-01-20"}
          result (SUT/round-robin-match users history)]
      ;; U1-U3 is the oldest pairing, should be matched
      (is (some #(= (set %) #{"U1" "U3"})
                (map set (::SUT/matched-pairs result))))
      (is (spec/valid? ::SUT/matches result))))

  (testing "handles empty history (all pairs have same priority)"
    (let [users ["U1" "U2" "U3" "U4"]
          history {}
          result (SUT/round-robin-match users history)]
      ;; Should still produce valid matches
      (is (= 2 (count (::SUT/matched-pairs result))))
      (is (not (contains? result ::SUT/unmatched-user)))
      (is (spec/valid? ::SUT/matches result)))))

(deftest round-robin-match-odd-users-test
  (testing "handles odd number of users"
    (let [users ["U1" "U2" "U3"]
          history {}
          result (SUT/round-robin-match users history)]
      (is (= 1 (count (::SUT/matched-pairs result))))
      (is (contains? result ::SUT/unmatched-user))
      (is (spec/valid? ::SUT/matches result))))

  (testing "unmatched user is one of the input users"
    (let [users ["U1" "U2" "U3"]
          history {}
          result (SUT/round-robin-match users history)]
      (is (contains? (set users) (::SUT/unmatched-user result))))))

(deftest round-robin-match-small-groups-test
  (testing "handles two users"
    (let [users ["U1" "U2"]
          history {}
          result (SUT/round-robin-match users history)]
      (is (= 1 (count (::SUT/matched-pairs result))))
      (is (not (contains? result ::SUT/unmatched-user)))))

  (testing "handles single user"
    (let [users ["U1"]
          history {}
          result (SUT/round-robin-match users history)]
      (is (= 0 (count (::SUT/matched-pairs result))))
      (is (= "U1" (::SUT/unmatched-user result)))))

  (testing "handles empty user list"
    (let [users []
          history {}
          result (SUT/round-robin-match users history)]
      (is (= 0 (count (::SUT/matched-pairs result))))
      (is (not (contains? result ::SUT/unmatched-user))))))

