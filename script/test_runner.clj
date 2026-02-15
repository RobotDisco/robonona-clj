#!/usr/bin/env bb
;;; SPDX-License-Identifier: EPL-1.0
;;;
;;; Babashka-native test runner with metadata filtering.

(ns test-runner
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :as test]))

(defn path->ns
  "Convert a test file path to a namespace symbol"
  [path]
  (let [path-str (str path)
        ;; Find where 'test/' appears and strip everything up to and including it
        test-idx (str/index-of path-str "test/")
        relative (if test-idx
                   (subs path-str (+ test-idx (count "test/")))
                   path-str)]
    (-> relative
        (str/replace #"\.clj$" "")
        (str/replace "/" ".")
        (str/replace "_" "-")
        symbol)))

(defn find-test-nses
  "Find all test namespaces from test/**/*_test.clj files"
  []
  (->> (fs/glob "test" "**/*_test.clj")
       (map path->ns)
       ;; Skip commented-out/legacy test files
       (remove #{'robot-disco.robonona.legacy.mattermost-test})))

(defn get-test-vars
  "Get all test vars from namespaces"
  [nses]
  (doseq [ns nses] (require ns))
  (->> nses
       (mapcat #(->> (ns-publics %)
                     vals
                     (filter (fn [v] (:test (meta v))))))))

(defn run-tests
  "Run tests, optionally excluding by metadata.
   exclude-meta: set of metadata keys - tests with ANY of these keys will be skipped"
  [& {:keys [exclude-meta] :or {exclude-meta #{}}}]
  (let [nses (find-test-nses)
        all-vars (get-test-vars nses)
        ;; EXCLUSION HAPPENS HERE: remove vars that have any excluded metadata key
        vars-to-run (if (empty? exclude-meta)
                      all-vars
                      (remove (fn [v]
                                (some #(get (meta v) %) exclude-meta))
                              all-vars))]
    (println "Running" (count vars-to-run) "tests"
             (if (empty? exclude-meta)
               "(all tests)"
               (str "(excluding " exclude-meta ")")))
    (println)
    (binding [test/*report-counters* (ref test/*initial-report-counters*)]
      (test/do-report {:type :begin-test-run})
      (test/test-vars vars-to-run)
      (test/do-report (assoc @test/*report-counters* :type :summary))
      (let [{:keys [fail error]} @test/*report-counters*]
        (System/exit (if (pos? (+ fail error)) 1 0))))))

;; CLI entry point
(let [args (set *command-line-args*)]
  (cond
    (contains? args "--help")
    (do
      (println "Usage: bb script/test_runner.clj [options]")
      (println)
      (println "Options:")
      (println "  --all           Run all tests (including :integration)")
      (println "  --help          Show this help")
      (println)
      (println "By default, tests tagged with ^:integration are excluded."))

    (contains? args "--all")
    (run-tests)

    :else
    (run-tests :exclude-meta #{:integration})))
