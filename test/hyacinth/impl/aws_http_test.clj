(ns hyacinth.impl.aws-http-test
  (:require [midje.sweet :refer :all]
            [hyacinth.contract :refer :all]

            [hyacinth.core :refer [->uri->location]]
            [hyacinth.impl.aws-http :refer :all]
            [hyacinth.test-support :as test-support]))


(def props (test-support/props "donotcheckin/s3.http.properties"))

(when @props
  (facts "aws http buckets work"
    (check-contract (->s3-bucket @props)
                    (->uri->location :bucket-name->s3-location
                                     (fn [bucket-name]
                                       (->s3-bucket (assoc @props :bucket-name bucket-name)))))))


(future-fact "We can list more than the max-keys (default 1000)")

(future-fact "We automatically use multipart PUT object when passed a stream")

(future-fact "We can specify that a PUT request should be multipart")

(future-fact "We can specify that GET requests as multipart requests")

(future-fact "Handle exceptions sensibly on all operations, including authentication issues")
