(ns robot-disco.robonona.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn config [profile]
  (aero/read-config (io/resource "config.edn") {:profile profile}))

(defn mattermost-token [config]
  (get-in config [:mattermost :token]))

(defn mattermost-host [config]
  (get-in config [:mattermost :host]))

(defn mattermost-team [config]
  (get-in config [:mattermost :team]))

(defn coffeebot-channel [config]
  (get-in config [:coffeebot :channel]))

(defn coffeebot-dry-run [config]
  (get-in config [:coffeebot :dry-run]))

