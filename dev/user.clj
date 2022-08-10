(ns user
  (:require [clojure.spec.gen.alpha :as spec-gen]
            [clojure.spec.test.alpha :as spec-test]
            [robot-disco.robonona.mattermost :as mattermost]
            [robot-disco.robonona.coffeebot :as coffeebot]))

(defn instrument []
  (spec-test/instrument))

(defn coffeebot-run []
  (coffeebot/run "mattermost.internal.tulip.io"
    (System/getenv "ROBONONA_MATTERMOST_TOKEN")
    "general"
    "coffeebot-dev"))

(defn coffeebot-dry-run []
  (coffeebot/run "mattermost.internal.tulip.io"
    (System/getenv "ROBONONA_MATTERMOST_TOKEN")
    "general"
    "coffeebot-dev"
    :dry-run true))

(comment

  (coffeebot-run)

  (coffeebot-dry-run)

  (instrument)



  )  ;; Comment ends here
