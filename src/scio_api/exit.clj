(ns scio-api.exit)

(defn exit
  "Print message to stderr and exit with exit code"
  [msg exitcode]
  (.println *err* msg)
  (System/exit exitcode))
