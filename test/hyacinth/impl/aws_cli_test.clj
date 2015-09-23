(ns hyacinth.impl.aws-cli-test
  (:require [midje.sweet :refer :all]
            [hyacinth.contract :refer :all]

            [hyacinth.core :refer [->uri->location]]
            [hyacinth.impl.aws-cli :refer :all]
            [hyacinth.test-support :as test-support]))

(def props (test-support/props "donotcheckin/s3.cli.properties"))

(when @props
  (facts "aws cli buckets work"
    (clojure.pprint/pprint @props)
    (check-contract (->s3-bucket @props)
                    (->uri->location :bucket-name->s3-location
                                     (fn [bucket-name]
                                       (->s3-bucket (assoc @props :bucket-name bucket-name)))))))
