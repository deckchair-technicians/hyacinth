(ns hyacinth.impl.aws-cli
  (:require
    [cheshire.core :as json]
    [hyacinth
     [util :refer [with-temp-dir to-file strip-slashes join-paths]]
     [protocol :refer :all]]
    [hyacinth.aws.cli :refer :all])

  (:import [java.net URI]
           (java.io File)))


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
    (->s3-location aws-cli bucket-name (join-paths location-key relative-key)))

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
  (relative [_this relative-key]
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
  [{:keys [bucket-name profile] :as _opts}]
  (let [region (-> (apply sh (concat ["aws" "s3api"]
                                     (when profile ["--profile" profile])
                                     ["get-bucket-location" "--bucket" bucket-name]))
                   :out
                   (json/parse-string keyword)
                   :LocationConstraint)]
    (S3Bucket. (->BucketAwsCli bucket-name region profile)
               bucket-name)))
