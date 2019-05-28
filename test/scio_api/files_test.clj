(ns scio-api.files-test
  (:require [clojure.test :refer :all]
            [scio-api.files :refer :all]))

(deftest test-get-content-type
  (is (= "application/pdf" (get-content-type "/tmp/test pdf.pdf")))
  (is (= "application/pdf" (get-content-type "/tmp/test.pdf")))
  (is (= "application/binary" (get-content-type "/tmp/something.unknown"))))
