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
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
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
      (do
        (log/error (.getMessage e))
        []))))

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
  "Try to save te file to a location. Any errors is returned in the 'error' field of the map"
  [file-object content]
  (try (with-open [out (output-stream file-object)]
         (.write out content)
         {:error nil
          :filename (str file-object)
          :bytes (count content)})
       (catch Exception e
         (let [msg (.getMessage e)]
           (log/error msg)
           {:error msg
            :filename (str file-object)
            :count 0}))))

(defn register-submit
  "Register the filename of the saved file to the message queue for consumption by scio"
  [ini filename]
  (let [{:keys [host port queue]} (:beanstalk ini)]
    (try
      (with-beanstalkd (beanstalkd-factory host (Integer. port))
        (use-tube queue)
        (put (json/write-str {:filename filename}))
        {:error nil})
      (catch java.net.ConnectException e
        (let [msg "Unable to connect to message queue"]
          (log/error msg)
          {:error msg})))))

(defn handle-submit
  "Handle the submit api call. Write the content to the path specified in the
  storage sectuin in the .ini  file"
  [body]
  (let [content (b64/decode (:content body))
        ini (clojure-ini/read-ini (get-config-file) :keywordize? true)
        file-name (.getName (file (:filename body))) ;; get basename to avoid directory traversal
        file-object (file (get-in ini [:storage :storagedir]) file-name)
        save-result (save-file file-object content)]
    (if (nil? (:error save-result))
      (let [submit-result (register-submit ini (:filename save-result))]
        (if (nil? (:error submit-result))
            {:status 200
             :body save-result}
            {:status 500
             :body submit-result}))
      {:status 500
       :body save-result})))

(defn error-404
  "Return a map with error message"
  [msg]
  (do
    (log/warn msg)
    {:status 404
     :bytes 0
     :body {:error msg
            :bytes 0
            :filename nil
            :content nil
            :encoding nil}}))

(defn es-lookup-filename
  "Lookup filename from id in the elastic search cluster"
  [id]
  (let [ini (clojure-ini/read-ini (get-config-file) :keywordize? true)
        hosts (str/split
               (get-in ini [:elasticsearch :host])
               #"\s")
        client (spandex/client {:hosts hosts})]
    (try
      (spandex/request client {:url [:scio :_search]
                               :method :get
                               :body {:query {:match {:_id id}}}})
      (catch java.net.ConnectException e
        (let [msg  "Unable to connect to elastic search"]
          (log/error msg)
          {:error msg}))
      (catch java.lang.Exception e
        (let [msg (.getMessage e)]
          (log/error msg)
          {:error msg})))))

(defn handle-download
  "Look up id in the elastic search cluster to get local filename, read and return."
  [id]
  (let [ini (clojure-ini/read-ini (get-config-file) :keywordize? true)
        response (es-lookup-filename id)]
    (if-let [file-name (get-in response [:body :hits :hits 0 :_source :filename])]
      (let [file-base-name (.getName (file file-name))
            content (slurp-bytes (str (file (get-in ini [:storage :storagedir]) file-base-name)))]
        (if (seq content)
          {:status 200
           :body {:error nil
                  :bytes (count content)
                  :filename file-base-name
                  :content (b64/encode content)
                  :encoding "base64"}}
          (error-404 (str "Unable to read file " file-base-name))))
      (error-404 (if-let [msg (:error response)]
                   msg
                   (str "ID not found: " id))))))

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

(defn usage
  "Usage summary, Exit after call"
  []
  (do
    (println "\nUsage: java -jar scio-api-VERSION.jar [OPTION...]\n")
    (println "-c, --config=CONFIGFILE      Specify ini location")
    (println "-h, --help                   print this text")
    (println "\nYou can also override the default location of the .ini file by exporting the SCIOAPIINI environment variable.")
    (println "\nreport bugs to opensource@mnemonic.no")
    (System/exit 0)))

(defn -main
  [& args]
  (let [cli-args (parse-opts args cli-options)
        options (:options cli-args)
        errors (:errors cli-args)]
    (if errors
      (exit (clojure.string/join ", " errors) 1)
      (do
        (when (:help options)
          (usage))
        (System/setProperty "*scio-api-ini*" (:config options))
        (let [ini (clojure-ini/read-ini (get-config-file) :keywordize? true)]
          (run-jetty api
                     {:port (Integer. (get-in ini [:api :port] 3000))}))))))
