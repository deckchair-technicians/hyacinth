(ns hyacinth.impl.memory-test
  (:require [midje.sweet :refer :all]
            [hyacinth
             [contract :refer :all]
             [core :refer [location-factory]]]
            [hyacinth.impl.memory :refer :all]))

(facts "memory buckets work"
  (let [memory-bucket-atom (atom {})
        uri->location      (location-factory :memory-bucket-atom memory-bucket-atom)]
    (check-contract (->memory-bucket memory-bucket-atom "root-bucket")
                    uri->location)))
