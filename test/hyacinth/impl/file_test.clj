(ns hyacinth.impl.file-test
  (:require [midje.sweet :refer :all]

            [hyacinth
             [util :refer [with-temp-dir]]
             [contract :refer :all]
             [core :refer [location-factory]]]
            [hyacinth.impl.file :refer :all]))

(facts "file buckets work"
  (with-temp-dir [data-dir "hyacinth-file-test"]
    (println "Running file tests in " (.getAbsolutePath data-dir))
    (check-contract (->file-bucket data-dir "test-bucket") (location-factory :file-bucket-root data-dir))))
