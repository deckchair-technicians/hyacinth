(ns hyacinth.impl.aws-cli
  (:require [clojure.java
             [shell :as shell]
             [io :as io]]

            [clojure.string :as s]

            [hyacinth
             [util :refer [with-temp-dir to-file strip-slashes]]
             [protocol :refer :all]])

  (:import (java.io File IOException)
           [clojure.lang ExceptionInfo]
           [java.net URI]))

(defn join-path [^String path & paths]
  (.getPath (reduce (fn [^File acc ^String segment] (File. acc segment)) (File. path) paths)))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (not= 0 (:exit result))
      (throw (ex-info (str result " while running: " (s/join " " args)) result))
      result)))

(defn is-404-equivalent [e]
  (let [d (ex-data e)]
    (and (= 1 (:exit d))
         (or (re-find #".*(NoSuchKey|NoSuchBucket|\(404\)).*" (:err d))
             (s/blank? (:err d))))))

(defmacro throw-no-such-key [url & body]
  `(try
     ~@body
     (catch ExceptionInfo e#
       (if (is-404-equivalent e#)
         (throw (IOException. (str "Could not get " ~url) e#))
         (throw e#)))))

(defmacro nil-on-no-such-key [& body]
  `(try
     ~@body
     (catch ExceptionInfo e#
       (if (is-404-equivalent e#)
         nil
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

  (get-stream [_this]
    (with-temp-dir
      [dir "hyacinth"]
      (let [s3-url (str "s3://" (join-path bucket-name location-key))
            file (File. ^File dir "forstreaming")]
        (throw-no-such-key (str bucket-name "/" location-key)
                           (sh "aws" "s3" "cp"
                               s3-url
                               (.getAbsolutePath file)))

        (io/input-stream file))))

  (relative [_this relative-key]
    (->s3-location bucket-name (.getPath (File. ^String location-key ^String  relative-key))))

  (delete! [this]
    (when (has-data? this)
      (let [s3-url (str "s3://" (join-path bucket-name location-key))]
        (assert (= 0 (:exit (sh "aws" "s3" "rm" s3-url)))))))

  (child-keys [this]
    (->> (descendant-keys this)
         (map trim-after-first-slash)
         (into #{})))

  (descendant-keys [_this]
    (nil-on-no-such-key (list-descendants-relative bucket-name location-key)))

  (has-data? [_this]
    (some #{location-key} (nil-on-no-such-key (list-descendants bucket-name location-key))))

  (location-key [_this]
    location-key)

  (uri [_this]
    (URI. (str "s3://" bucket-name "/" location-key)))

  Object
  (toString [this] (uri this)))

(defn ->s3-location
  [bucket-name location-key]
  (S3Location. bucket-name (strip-slashes location-key)))

(deftype S3Bucket [bucket-name]
    BucketLocation
  (relative [this relative-key]
    (->s3-location bucket-name relative-key))

  ; Delegate to Amazonica S3 implementation
  (put! [_this _obj]
    (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

  (delete! [_this]
    (throw (UnsupportedOperationException. "Can't delete buckets")))

  (get-stream [_this]
    (throw (UnsupportedOperationException. "Can't get data directly from a bucket- specify a descendant")))

  (has-data? [_this]
    false)

  (child-keys [_this]
    (list-descendants bucket-name "") )

  (descendant-keys [_this]
    (list-descendants bucket-name "") )

  (location-key [_this]
    nil)

  (uri [_this]
    (URI. (str "s3://" bucket-name)))

  Object
  (toString [this] (uri this)))

(defn ->s3-bucket
  [bucket-name]
  (S3Bucket. bucket-name))
