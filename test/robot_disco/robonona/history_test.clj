;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.history-test
  (:require
   [clojure.spec.test.alpha :as spec-test]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [robot-disco.robonona.history :as SUT]))

;;; Instrumentation fixture
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn instrumentation-fixture [f]
  (spec-test/instrument)
  (f)
  (spec-test/unstrument))

(use-fixtures :once instrumentation-fixture)

;;; Unit tests
;;;;;;;;;;;;;;

(deftest make-pair-key-test
  (testing "creates sorted pair from two user IDs"
    (is (= ["U1" "U2"] (SUT/make-pair-key "U1" "U2")))
    (is (= ["U1" "U2"] (SUT/make-pair-key "U2" "U1"))))
  (testing "handles alphabetically later IDs"
    (is (= ["UA" "UB"] (SUT/make-pair-key "UB" "UA")))
    (is (= ["A1" "Z9"] (SUT/make-pair-key "Z9" "A1")))))

(deftest prune-history-test
  (testing "removes pairs where either user is inactive"
    (let [history {["U1" "U2"] #inst "2026-01-01"
                   ["U1" "U3"] #inst "2026-01-08"
                   ["U2" "U3"] #inst "2026-01-15"}
          active-users #{"U1" "U2"}]
      (is (= {["U1" "U2"] #inst "2026-01-01"}
             (SUT/prune-history history active-users)))))
  (testing "keeps all pairs when all users are active"
    (let [history {["U1" "U2"] #inst "2026-01-01"
                   ["U1" "U3"] #inst "2026-01-08"}
          active-users #{"U1" "U2" "U3"}]
      (is (= history (SUT/prune-history history active-users)))))
  (testing "returns empty map when no users are active"
    (let [history {["U1" "U2"] #inst "2026-01-01"}
          active-users #{}]
      (is (= {} (SUT/prune-history history active-users))))))

(deftest update-history-test
  (testing "adds new pairs to empty history"
    (let [now #inst "2026-02-12"
          pairs [["U1" "U2"] ["U3" "U4"]]
          result (SUT/update-history {} pairs now)]
      (is (= {["U1" "U2"] now
              ["U3" "U4"] now}
             result))))
  (testing "updates existing pairs with new timestamp"
    (let [old-time #inst "2026-01-01"
          new-time #inst "2026-02-12"
          history {["U1" "U2"] old-time}
          pairs [["U1" "U2"]]
          result (SUT/update-history history pairs new-time)]
      (is (= {["U1" "U2"] new-time} result))))
  (testing "normalizes pair order when updating"
    (let [now #inst "2026-02-12"
          ;; Pair given in reverse order
          pairs [["U2" "U1"]]
          result (SUT/update-history {} pairs now)]
      ;; Should be stored in canonical order
      (is (= {["U1" "U2"] now} result)))))

;;; I/O tests
;;;;;;;;;;;;;

(deftest read-history-test
  (testing "returns empty state when file doesn't exist"
    (let [result (SUT/read-history "/tmp/nonexistent-history-file-12345.edn")]
      (is (= {} (::SUT/pairing-history result)))))

  (testing "reads existing history file"
    (let [temp-file (java.io.File/createTempFile "history-test" ".edn")
          state {::SUT/pairing-history {["U1" "U2"] #inst "2026-02-12"}
                 ::SUT/last-run #inst "2026-02-12"}]
      (try
        (spit temp-file (pr-str state))
        (let [result (SUT/read-history (.getPath temp-file))]
          (is (= {["U1" "U2"] #inst "2026-02-12"}
                 (::SUT/pairing-history result)))
          (is (= #inst "2026-02-12" (::SUT/last-run result))))
        (finally
          (.delete temp-file))))))

(deftest write-history-test
  (testing "writes state to file"
    (let [temp-file (java.io.File/createTempFile "history-test" ".edn")
          state {::SUT/pairing-history {["U1" "U2"] #inst "2026-02-12"}
                 ::SUT/last-run #inst "2026-02-12"}]
      (try
        (SUT/write-history (.getPath temp-file) state)
        (let [result (SUT/read-history (.getPath temp-file))]
          (is (= (::SUT/pairing-history state)
                 (::SUT/pairing-history result))))
        (finally
          (.delete temp-file))))))
