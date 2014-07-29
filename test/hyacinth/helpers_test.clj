(ns hyacinth.helpers-test
  (:require [midje.sweet :refer :all]
            [hyacinth.memory :refer [->memory-bucket]]
            [hyacinth.protocol :refer :all]))

(fact "copy-location works"
      (let [buckets-atom (atom {})
            from (relative (->memory-bucket buckets-atom "from") "file")
            to (relative (->memory-bucket buckets-atom "to") "file")]
        (put! from "hello")
        (copy-location from to)
        (slurp (get-stream to)) => "hello"))
