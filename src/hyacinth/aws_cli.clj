(ns hyacinth.aws-cli
  (:require [clojure.java
             [shell :as shell]
             [io :as io]]

            [clojure.string :as s]

            [hyacinth
             [util :refer [with-temp-dir to-file]]
             [protocol :refer :all]])

   (:import (java.io File IOException)))

(defn join-path [path & paths]
  (.getPath (reduce (fn [acc segment] (File. acc segment)) (File. path) paths)))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (not= 0 (:exit result))
      (throw (RuntimeException. (str result)))
      result)))

(defmacro throw-no-such-key [url & body]
  `(try
     ~@body
     (catch RuntimeException e#
       (if (re-matches #".*NoSuchKey.*" (.getMessage e#))
         (throw (IOException. (str "Could not get " ~url) e#))
         (throw e#)))))

(defn replace-prefix [prefix]
  (let [pattern (re-pattern (str "^" (s/re-quote-replacement prefix) "/?(.*)"))]
    (fn [s]
      (assert (re-matches pattern s) (str pattern " does not match '" s "'"))
      (s/replace s pattern (fn [[_ x & _]] x)))))

(defn list-descendants
  [bucket-name prefix]
  (let [{:keys [out]} (sh "aws" "s3" "ls" (str "s3://" bucket-name "/" prefix) "--recursive")]
    (->> (clojure.string/split-lines out)
         (map
          (fn [s]
            (last
             (re-find
              #"[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} *[0-9]* (.*)$"
              s))))
         (filter identity))))

(defn list-descendants-relative
  [bucket-name prefix]
  (->> (list-descendants bucket-name prefix)
       (map (replace-prefix prefix))
       (remove empty?)))

(defn trim-after-first-slash [s]
  (s/replace s #"/.*" ""))


(declare ->s3-location)
(deftype S3Location [bucket-name location-key]
         BucketLocation
  (put! [this obj]
    (with-temp-dir [dir "hyacinth"]
                   (throw-no-such-key (str bucket-name "/" location-key)
                                      (sh "aws" "s3" "cp"
                                          (to-file dir obj)
                                          (str "s3://" (join-path bucket-name location-key)))))
    this)

  (get-stream [this]
    (with-temp-dir
      [dir "hyacinth"]
      (let [s3-url (str "s3://" (join-path bucket-name location-key))
            file (File. dir "forstreaming")]
        (throw-no-such-key (str bucket-name "/" location-key)
                           (sh "aws" "s3" "cp"
                               s3-url
                               (.getAbsolutePath file)))

        (io/input-stream file))))

  (relative [this relative-key]
    (->s3-location bucket-name (.getPath (File. location-key relative-key))))

  (delete! [this]
    (when (has-data? this)
      (let [s3-url (str "s3://" (join-path bucket-name location-key))]
        (assert (= 0 (:exit (sh "aws" "s3" "rm" s3-url)))))))

  (child-keys [this]
    (->> (list-descendants-relative bucket-name (str location-key "/"))
         (map trim-after-first-slash)
         (into #{})))

  (descendant-keys [this]
    (list-descendants-relative bucket-name location-key))

  (has-data? [this]
    (some #{location-key} (list-descendants bucket-name location-key)))

  Object
  (toString [this] (str "S3Location '" bucket-name "/" location-key "'")))

(defn ->s3-location
  [bucket-name location-key]

  (S3Location. bucket-name location-key))

(deftype S3Bucket [bucket-name]
    BucketLocation
  (relative [this relative-key]
    (->s3-location bucket-name relative-key))

  ; Delegate to Amazonica S3 implementation
  (put! [this obj]
    (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

  (delete! [this]
    (throw (UnsupportedOperationException. "Can't delete buckets")))

  (get-stream [this]
    (throw (UnsupportedOperationException. "Can't get data directly from a bucket- specify a descendant")))

  (has-data? [this]
    false)

  (child-keys [this]
    (list-descendants bucket-name "") )

  Object
  (toString [this] (str "S3Bucket '" bucket-name "'")))

(defn ->s3-bucket
  [bucket-name]
  (S3Bucket. bucket-name))
