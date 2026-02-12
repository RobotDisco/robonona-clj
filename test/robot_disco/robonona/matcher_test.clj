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

