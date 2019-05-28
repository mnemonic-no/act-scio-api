(ns scio-api.files
  (:import [java.nio.file Files Paths]
           [java.io FileNotFoundException ByteArrayOutputStream])
  (:require [clojure.java.io :refer [file output-stream input-stream copy]]
            [clojure.tools.logging :as log]))


(defn get-content-type
  "Probe the content type of a file"
  [file-name]
  (if-let [content-type (Files/probeContentType
                         (Paths/get (.toURI (file file-name))))]
    content-type
    "application/binary"))

(defn elem-of?
  "True if elm is in coll"
  [elm coll]
  (some #(= elm %) coll))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (try
    (with-open [out (ByteArrayOutputStream.)]
      (copy (input-stream (file x)) out)
      (.toByteArray out))
    (catch FileNotFoundException e
      (do
        (log/error (.getMessage e))
        []))))

(defn get-safe-content-type
  "Probe the content type of a file, check if it is configured as safe. If not, return application/binary"
  [file-name accepted-types]
  (let [probed-content-type (get-content-type file-name)]
    (if (elem-of? probed-content-type accepted-types)
      probed-content-type
      "application/binary")))

(defn save-file
  "Try to save the file to a location. Any errors is returned in the 'error' field of the map"
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
