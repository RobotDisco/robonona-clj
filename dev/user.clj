(ns user
  (:require [clojure.spec.gen.alpha :as spec-gen]
            [clojure.spec.test.alpha :as spec-test]
            [robot-disco.robonona.main :as main]
            [robot-disco.robonona.mattermost :as mattermost]
            [robot-disco.robonona.coffeebot :as coffeebot]
            [robot-disco.robonona.config :as config]))

(defn instrument []
  (spec-test/instrument))

(defn coffeebot-dry-run []
  (let [config (config/config :dev)]
    (main/run config)))

(defn coffeebot-integration-run []
  (let [config (assoc-in (config/config :dev)
                         [:coffeebot :dry-run] false)]
    (main/run config)))

(defn coffeebot-prod-dry-run []
  (let [config (assoc-in (config/config :prod)
                         [:coffeebot :dry-run] true)]
    (main/run config)))

(defn coffeebot-prod-run []
  (let [config (config/config :prod)]
    (main/run config)))

(comment

  (coffeebot-integration-run)

  (coffeebot-dry-run)

  (coffeebot-prod-dry-run)

  (coffeebot-prod-run)

  (instrument)



  )  ;; Comment ends here
