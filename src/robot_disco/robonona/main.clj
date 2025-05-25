#!/usr/bin/env bb
;;; SPDX-License-Identifier: EPL-1.0

(ns robot-disco.robonona.main
  (:require [babashka.cli :as cli]))

(def ^:const VERSION "2.0.0-dev")

(def cli-spec {})

(defn -main [& _]
  (println "Robonona version" VERSION)
  (println "Usage: " (cli/format-opts {:spec cli-spec})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
