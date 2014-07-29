(ns hyacinth.memory-test
  (:require [midje.sweet :refer :all]
            [hyacinth
             [contract :refer :all]
             [protocol :refer :all]
             [memory :refer :all]]))

(facts "memory buckets work"
       (check-contract (->memory-bucket (atom {}) "root-bucket")))
