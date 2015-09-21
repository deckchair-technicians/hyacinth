(ns hyacinth.impl.memory
  (:require [clojure.java.io :as io]
            [clojure.string :as s]

            [hyacinth
             [util :refer [dissoc-in* join-path-forward-slash]]
             [protocol :refer :all]])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream IOException)
           [java.net URI]))

(defn split-path [path]
  (s/split path #"/"))

(defn get-child-keys [buckets-atom path]
  (when-let [entry (get-in @buckets-atom (split-path path))]
    (when (map? entry)
      (keys entry))))

(defn get-descendant-keys [x]
  (if-not (map? x)
    []
    (->> x
         (map (fn [[k v]]
                (if (map? v)
                  (map #(str k "/" %)
                       (get-descendant-keys v))
                  [k])))
         (mapcat identity))))

(defn memory-location
  [buckets-atom ^String path]
  (let [key-path (split-path path)]
    (reify BucketLocation
      (put! [this obj]
        (let [stream (ByteArrayOutputStream.)]
          (io/copy (coerce-to-streamable obj) stream)
          (swap! buckets-atom #(assoc-in % key-path (.toByteArray stream))))
        this)

      (delete! [this]
        (swap! buckets-atom #(dissoc-in* % key-path))
        nil)

      (get-stream [this]
        (if (has-data? this)
          (ByteArrayInputStream. (get-in @buckets-atom key-path))
          (throw (IOException. (str "No data for " path)))))

      (child-keys [this]
        (or (get-child-keys buckets-atom path) []))

      (descendant-keys [this]
        (get-descendant-keys (get-in @buckets-atom key-path)))

      (relative [this relative-key]
        (memory-location buckets-atom (join-path-forward-slash path relative-key)))

      (has-data? [this]
        (instance? (Class/forName "[B") (get-in @buckets-atom key-path)))

      (location-key [this]
        (clojure.string/join "/" (drop 1 key-path)))

      (uri [this]
        (URI. (str "mem://" path)))

      Object
      (toString [this] (str "MemoryBucket '" path "'")))))

(defn ->memory-bucket
  [buckets-atom bucket-name]
  (assert buckets-atom)
  (assert bucket-name)
  (reify BucketLocation
    (put! [this obj]
      (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

    (delete! [this]
      (throw (UnsupportedOperationException. "Can't delete buckets")))

    (get-stream [this]
      (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

    (has-data? [this]
      false)

    (descendant-keys [this]
      (get-descendant-keys (get-in @buckets-atom bucket-name)))

    (child-keys [this]
      (get-child-keys buckets-atom bucket-name))

    (relative [this relative-key]
      (assert relative-key)
      (memory-location buckets-atom (join-path-forward-slash bucket-name relative-key)))

    (location-key [this]
      nil)

    (uri [this]
      (URI. (str "mem://" bucket-name)))

    Object
    (toString [this] (str "MemoryBucket '" bucket-name "'"))))
