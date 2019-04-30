(ns scio-api.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [scio-api.b64 :as b64]
            [qbits.spandex :as spandex]
            [clojure-ini.core :as clojure-ini]
            [clojure.java.io :refer [file output-stream input-stream copy]]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [beanstalk-clj.core :refer [with-beanstalkd beanstalkd-factory
                                        put use-tube]]
            [ring.adapter.jetty :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]))


(if-let [ini-file (System/getenv "SCIOAPIINI")]
  (System/setProperty "*scio-api-ini*" ini-file)
  (System/setProperty "*scio-api-ini*" "/etc/scioapi.ini"))

(defn exit
  "Print message to stderr and exit with exit code"
  [msg exitcode]
  (.println *err* msg)
  (System/exit exitcode))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (try
    (with-open [out (java.io.ByteArrayOutputStream.)]
      (copy (input-stream (file x)) out)
      (.toByteArray out))
    (catch java.io.FileNotFoundException e
      [])))

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

(defn save-file
  ""
  [file-object content]
  (try (with-open [out (output-stream file-object)]
         (.write out content)
         {:error nil
          :filename (str file-object)
          :bytes (count content)})
       (catch Exception e {:error (.getMessage e)
                           :filename (str file-object)
                           :count 0})))

(defn register-submit
  ""
  [ini filename]
  (let [{:keys [host port queue]} (:beanstalk ini)]
    (with-beanstalkd (beanstalkd-factory host (Integer. port))
      (use-tube queue)
      (put filename))))

(defn handle-submit
  "Handle the submit api call. Write the content to the path specified in the
  storage sectuin in the .ini  file"
  [body]
  (let [_ (spit "/tmp/a" body)
        content (b64/decode (:content body))
        ini (clojure-ini/read-ini (get-config-file) :keywordize? true)
        file-name (.getName (file (:filename body))) ;; get basename to avoid directory traversal
        file-object (file (get-in ini [:storage :storagedir]) file-name)
        result (save-file file-object content)]
    (if (nil? (:error result))
      (do
        (register-submit ini (:filename result))
        {:status 200
         :bytes (:bytes result)
         :body "ok"})
      {:status 500
       :bytes 0
       :body (:error result)})))

(defn handle-download
  ""
  [id]
  (let [ini (clojure-ini/read-ini (get-config-file) :keywordize? true)
        host (get-in ini [:elasticsearch :host])
        hosts (str/split host #"\s")
        client (spandex/client {:hosts hosts})
        response (spandex/request client {:url [:scio :_search]
                                          :method :get
                                          :body {:query {:match {:_id id}}}})]
    (if-let [file-name (get-in response [:body :hits :hits 0 :_source :filename])]
      (let [file-base-name (.getName (file file-name))
            content (slurp-bytes (str (file (get-in ini [:storage :storagedir]) file-base-name)))]
        (if (seq content)
          {:status 200
           :bytes (count content)
           :body {:filename file-base-name
                  :content (b64/encode content)
                  :encoding "base64"}}
          {:status 404
           :bytes 0
           :body {:filename file-base-name
                  :content ""
                  :encoding "base64"}}))
      {:status 404
       :bytes 0
       :body {:filename ""
              :content ""
              :encoding "base64"}})))

(defroutes api-routes
  (POST "/submit" {:keys [params]} (handle-submit params))
  (GET "/download" [id] (handle-download id))
  (route/not-found {:body {:error "Page not found"}}))

(def api
  (-> api-routes
      wrap-keyword-params
      (ring-json/wrap-json-params {:keywords? true :bigdecimals? true})
      wrap-params
      (ring-json/wrap-json-response)))

(def cli-options
  "CLI Options"
  [["-c" "--config CONFIG" "Config File"
    :id :config
    :default "/etc/scio.ini"
    :validate [#(.isFile (file %)) "File not found!"]]
   ["-h" "--help"]])


(defn -main
  [& args]
  (let [cli-args (parse-opts args cli-options)
        options (:options cli-args)
        errors (:errors cli-args)]
    (if errors
      (exit (clojure.string/join ", " errors) 1)
      (do 
        (System/setProperty "*scio-api-ini*" (:config options))
        (let [ini (clojure-ini/read-ini (get-config-file) :keywordize? true)]
          (run-jetty api
                     {:port (Integer. (get-in ini [:api :port] 3000))}))))))

