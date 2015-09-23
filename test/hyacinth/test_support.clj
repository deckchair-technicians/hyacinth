(ns hyacinth.test-support
  (:require [clojure.java.io :as io])
  (:import [java.util Properties]))

(defn load-properties [url]
  (doto (Properties.)
    (.load (io/input-stream url))))

(defn prop-map [url]
  (reduce (fn [xs [k v]] (assoc xs (keyword k) v))
          {}
          (load-properties url)))

(defn props [path]
  (delay
    (if-let [url (io/resource path)]
      (prop-map url)
      (println "S3 test not running- create"
               (str "test/" path)
               "to run test. This file should be in .gitignore. Double check it really is before commiting."))))
