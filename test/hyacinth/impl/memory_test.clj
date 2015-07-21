(ns hyacinth.impl.memory-test
  (:require [midje.sweet :refer :all]
            [hyacinth
             [contract :refer :all]
             [protocol :refer :all]]
            [hyacinth.impl.memory :refer :all]))

(facts "memory buckets work"
       (check-contract (->memory-bucket (atom {}) "root-bucket")))
