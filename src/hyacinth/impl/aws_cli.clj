(ns hyacinth.impl.aws-cli
  (:require [clojure.java
             [shell :as shell]
             [io :as io]]

            [clojure.string :as s]
            [cheshire.core :as json]

            [hyacinth
             [util :refer [with-temp-dir to-file strip-slashes]]
             [protocol :refer :all]])

  (:import (java.io File IOException)
           [clojure.lang ExceptionInfo]
           [java.net URI]))

(defn join-path-forward-slash [^String path & paths]
  (reduce
    (fn [path-left path-right]
      (clojure.string/replace (str path-left "/" path-right) #"/+" "/"))
    path
    paths))

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

(defn aws-sh [command region profile & args]
  (apply sh "aws" "s3" command
         (concat (when region ["--region" region])
                 (when profile ["--profile" profile])
                 args)))

(defn aws-cp [region profile from to]
  (aws-sh "cp" region profile from to))

(defprotocol AwsCli
  (ls [this location-key])
  (ls-recursive [this location-key])
  (cp-up [this from-input-stream to-location-key])
  (cp-down [this from-location-key to-file])
  (rm [this location-key]))

(defn list-descendants
  [aws-cli prefix]
  (let [{:keys [out]} (ls-recursive aws-cli prefix)]
    (->> (clojure.string/split-lines out)
         (map
           (fn [s]
             (last
               (re-find
                 #"[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} *[0-9]* (.*)$"
                 s))))
         (filter identity))))

(defn list-descendants-relative
  [aws-cli prefix]
  (->> (list-descendants aws-cli prefix)
       (map (replace-prefix prefix))
       (remove empty?)))

(defn trim-after-first-slash [s]
  (s/replace s #"/.*" ""))


(deftype BucketAwsCli [bucket-name region profile]
  AwsCli
  (ls [_ location-key]
    (aws-sh "ls" region profile (str "s3://" bucket-name "/" location-key)))

  (ls-recursive [this location-key]
    (aws-sh "ls" region profile (str "s3://" bucket-name "/" location-key) "--recursive"))

  (cp-up [_ from-input-stream to-location-key]
    (throw-no-such-key (str bucket-name "/" to-location-key)
                       (aws-cp region profile
                               from-input-stream
                               (str "s3://" (join-path-forward-slash bucket-name to-location-key)))))

  (cp-down [_ from-location-key to-file]
    (let [s3-url (str "s3://" (join-path-forward-slash bucket-name from-location-key))]
      (throw-no-such-key (str bucket-name "/" from-location-key)
                         (aws-cp region profile s3-url (.getAbsolutePath to-file)))

      (io/input-stream to-file)))

  (rm [_ location-key]
    (let [s3-url (str "s3://" (join-path-forward-slash bucket-name location-key))]
      ; TODO: This assert is gross.
      (assert (= 0 (:exit (aws-sh "rm" region profile s3-url)))))))

(declare ->s3-location)

(deftype S3Location [aws-cli bucket-name location-key]
  BucketLocation
  (put! [this obj]
    (with-temp-dir [dir "hyacinth"]
      (cp-up aws-cli
          (to-file dir obj)
          location-key))
    this)

  (get-stream [_this]
    (with-temp-dir
      [dir "hyacinth"]
      (cp-down aws-cli location-key (File. ^File dir "forstreaming"))))

  (relative [_this relative-key]
    (->s3-location aws-cli bucket-name (join-path-forward-slash location-key relative-key)))

  (delete! [this]
    (when (has-data? this)
      (rm aws-cli location-key)))

  (child-keys [this]
    (->> (descendant-keys this)
         (map trim-after-first-slash)
         (into #{})))

  (descendant-keys [_this]
    (nil-on-no-such-key (list-descendants-relative aws-cli location-key)))

  (has-data? [_this]
    (some #{location-key} (nil-on-no-such-key (list-descendants aws-cli location-key))))

  (location-key [_this]
    location-key)

  (uri [_this]
    (URI. (str "s3://" bucket-name "/" location-key)))

  Object
  (toString [this] (str (uri this))))

(defn ->s3-location
  [aws-cli bucket-name location-key]
  (S3Location. aws-cli bucket-name (strip-slashes location-key)))

(deftype S3Bucket [aws-cli bucket-name]
  BucketLocation
  (relative [this relative-key]
    (->s3-location aws-cli bucket-name relative-key))

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
    (list-descendants aws-cli ""))

  (descendant-keys [_this]
    (list-descendants aws-cli ""))

  (location-key [_this]
    nil)

  (uri [_this]
    (URI. (str "s3://" bucket-name)))

  Object
  (toString [this] (str (uri this))))

(defn ->s3-bucket
  [{:keys [bucket-name profile] :as opts}]
  (let [region (-> (apply sh (concat ["aws" "s3api"]
                                     (when profile ["--profile" profile])
                                     ["get-bucket-location" "--bucket" bucket-name]))
                   :out
                   (json/parse-string keyword)
                   :LocationConstraint)]
    (S3Bucket. (BucketAwsCli. bucket-name region profile)
               bucket-name)))
