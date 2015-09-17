(ns hyacinth.impl.aws-cli-test
  (:require [midje.sweet :refer :all]
            [hyacinth.contract :refer :all]

            [clojure.java.io :as io]
            [hyacinth.core :refer [->uri->location]]
            [hyacinth.impl.aws-cli :refer :all])
  (:import [java.util Properties]))

(facts "Path joining with forward-slash"

  (join-path-forward-slash "a") => "a"
  (join-path-forward-slash "a" "b" "c") => "a/b/c"
  (join-path-forward-slash "a/" "b/" "/c") => "a/b/c"
  (join-path-forward-slash "a////" "b/" "/c") => "a/b/c")

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
  (facts "aws cli buckets work"
    (clojure.pprint/pprint @props)
    (check-contract (->s3-bucket @props)
                    (->uri->location :bucket-name->s3-location
                                     (fn [bucket-name]
                                       (->s3-bucket (assoc @props :bucket-name bucket-name)))))))
