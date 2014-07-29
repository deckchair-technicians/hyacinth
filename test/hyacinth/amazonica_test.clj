(ns hyacinth.amazonica-test
  (:require [midje.sweet :refer :all]

            [clojure.java.io :as io]

            [hyacinth
             [protocol :refer :all]
             [contract :refer :all]
             [amazonica :refer :all]])

   (:import (java.util Properties Map$Entry)))

(defn load-properties [url]
  (doto (Properties.)
    (.load (io/input-stream url))))

(defn prop-map [url]
  (reduce (fn [xs [k v]] (assoc xs (keyword k) v))
          {}
          (load-properties url)))

(def props-path "donotcheckin/s3creds.properties")
(def props (delay
             (if-let [url (io/resource props-path)]
               (prop-map url)
               (println "S3 test not running- create"
                        (str "test/" props-path)
                        "to run test. This file should be in .gitignore. Double check it really is before commiting."))))

(when @props
  (facts "amazonica buckets work"
    (check-contract (->s3-bucket @props (:bucket @props)))))
