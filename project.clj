(defproject scio-api "0.1.0-SNAPSHOT"
  :description "API for uploading and downloading documents"
  :url "https://github.com/mnemonic-no/scio-api"
  :license {:name "ISC"
            :url "https://opensource.org/licenses/ISC"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-mock "0.3.2"]
                 [clojure-ini "0.0.2"]
                 [beanstalk-clj "0.1.3"]
                 [cc.qbits/spandex "0.6.3"]
                 [compojure "1.6.1"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler scio-api.core/api}
  :repl-options {:init-ns scio-api.core})
