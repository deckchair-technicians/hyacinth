(ns hyacinth.helpers-test
  (:import [java.io FileInputStream ByteArrayInputStream]
           [sun.misc IOUtils])
  (:require [midje.sweet :refer :all]
            [hyacinth.memory :refer [->memory-bucket]]
            [hyacinth.protocol :refer :all]))

(fact "copy-location works"
      (let [buckets-atom (atom {})
            from (relative (->memory-bucket buckets-atom "from") "file")
            to (relative (->memory-bucket buckets-atom "to") "file")
            input-data (byte-array (map byte [-1]))
            output-data (byte-array (count input-data))]
        (put! from input-data)
        (copy-location from to)

        (.read (get-stream to) output-data 0 (count input-data))

        (into [] output-data) => (into [] input-data)))
