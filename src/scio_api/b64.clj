(ns scio-api.b64)

(defn encode
  [to-encode]
  (let [encoder (java.util.Base64/getEncoder)]
    (.encodeToString encoder to-encode)))

(defn decode
  [to-decode]
  (let [decoder (java.util.Base64/getDecoder)]
    (->> to-decode
         (.decode decoder))))
