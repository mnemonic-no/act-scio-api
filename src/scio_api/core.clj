(ns scio-api.core
  (:gen-class)
  (:import [java.io FileInputStream])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [scio-api.b64 :as b64]
            [scio-api.files :refer :all]
            [scio-api.conf :refer :all]
            [scio-api.exit :refer :all]
            [qbits.spandex :as spandex]
            [clojure-ini.core :as clojure-ini]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [clojure.java.io :refer [file]]
            [clojure.data.json :as json]
            [beanstalk-clj.core :refer [with-beanstalkd beanstalkd-factory
                                        put use-tube]]
            [ring.adapter.jetty :refer :all]
            [ring.middleware.json :as ring-json]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]))


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
  storage section in the .ini  file"
  [body]
  (let [content (b64/decode (:content body))
        ini (read-ini)
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
  [id format]
  (let [ini (read-ini)
        response (es-lookup-filename id)]
    (if-let [file-name (get-in response [:body :hits :hits 0 :_source :filename])]
      (let [file-base-name (.getName (file file-name))
            safe-content-types (str/split (get-in ini [:storage :safe-content-types]) #"\s")
            file-storage-path (str (file (get-in ini [:storage :storagedir]) file-base-name))
            content-type (get-safe-content-type file-storage-path safe-content-types)
            content (slurp-bytes file-storage-path)]
        (if (seq content)
          (if (= format "json")
            {:status 200
             :body {:error nil
                    :bytes (count content)
                    :filename file-base-name
                    :content (b64/encode content)
                    :encoding "base64"}}
            {:status 200
             :headers {"Content-Type" content-type
                       "Content-Disposition", (str "attachment; filename=\"" file-base-name "\"")}
             :body (FileInputStream. file-storage-path)})
          (error-404 (str "Unable to read file " file-base-name))))
      (error-404 (if-let [msg (:error response)]
                   msg
                   (str "ID not found: " id))))))

(defroutes api-routes
  (POST "/submit" {:keys [params]} (handle-submit params))
  (GET "/download" [id format] (handle-download id format))
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
