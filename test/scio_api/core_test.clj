(ns scio-api.core-test
  (:require [clojure.test :refer :all]
            [scio-api.core :refer :all]
            [ring.mock.request :as mock]))

(deftest test-upload
  (System/setProperty "*scio-api-ini*" "sample.ini")
  (is (= {:status 200
          :bytes 4
          :body "ok"
          :headers {}}
         (api (-> (mock/request :post "/submit")
                  (mock/json-body {:filename "test.txt"
                                   :content "VGVzdA=="})))))
  (is (= {:status 200
          :bytes 4
          :body "ok"
          :headers {}}
         (api (-> (mock/request :post "/submit")
                  (mock/json-body {:filename "../../../../../../../../../../../../../../../root/evil.txt"
                                   :content "VGVzdA=="}))))))
 
