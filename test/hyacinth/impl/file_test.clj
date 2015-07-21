(ns hyacinth.impl.file-test
  (:require [midje.sweet :refer :all]

            [hyacinth
             [util :refer [with-temp-dir]]
             [contract :refer :all]]
            [hyacinth.impl.file :refer :all]))

(facts "file buckets work"
  (with-temp-dir [data-dir "hyacinth-file-test"]
                 (check-contract (->file-bucket data-dir))))
