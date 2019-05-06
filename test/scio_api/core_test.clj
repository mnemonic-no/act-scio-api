(ns scio-api.core-test
  (:require [clojure.test :refer :all]
            [scio-api.core :refer :all]
            [ring.mock.request :as mock]))

(deftest test-upload
  (System/setProperty "*scio-api-ini*" "etc/sample.ini")
  (is (= {:status 200
          :body "{\"error\":null,\"filename\":\"/tmp/test.txt\",\"bytes\":4}"
          :headers {"Content-Type" "application/json; charset=utf-8"}}
         (api (-> (mock/request :post "/submit")
                  (mock/json-body {:filename "test.txt"
                                   :content "VGVzdA=="})))))
  (is (= {:status 200
          :body "{\"error\":null,\"filename\":\"/tmp/evil.txt\",\"bytes\":4}"
          :headers {"Content-Type" "application/json; charset=utf-8"}}
         (api (-> (mock/request :post "/submit")
                  (mock/json-body {:filename "../../../../../../../../../../../../../../../root/evil.txt"
                                   :content "VGVzdA=="}))))))

