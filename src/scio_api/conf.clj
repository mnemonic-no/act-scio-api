(ns scio-api.conf
  (:require [clojure-ini.core :as clojure-ini]
            [clojure.java.io :refer [file]]
            [scio-api.exit :refer :all]))

(if-let [ini-file (System/getenv "SCIOAPIINI")]
  (System/setProperty "*scio-api-ini*" ini-file)
  (System/setProperty "*scio-api-ini*" "/etc/scioapi.ini"))

(defn get-config-file
  "read the config file path from the SCIOAPIINI environment path
  or default to /etc/scioapi.ini"
  []
  (let [cfg-file (System/getProperty "*scio-api-ini*")]
    (if (.isFile (file cfg-file))
      cfg-file
      (exit (str "\nCoult not find config file: "
                 cfg-file
                 "\nconsider to provide the SCIOAPIINI environment variable or use the '-c CONFIGFILE' argument.")
            1))))

(defn read-ini
  "read the config file as keywordized map"
  []
  (clojure-ini/read-ini (get-config-file) :keywordize? true))
