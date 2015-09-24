(ns hyacinth.impl.aws-http
  (:require [hyacinth.aws.s3 :refer :all]
            [hyacinth
             [util :refer [strip-slashes join-paths]]
             [protocol :refer :all]]
            [clojure.xml :as xml]
            [hyacinth.util :as util]
            [clojure.string :as string])
  (:import (java.net URI)
           (java.io IOException)))

(declare ->s3-location)

(defn throw-io-exception-non-200
  [{:keys [status body] :as response}]
  (if (= 200 status)
    response
    (throw (IOException. (str status (if (string? body) body (slurp body)))))))

(defn l-o [aws-http location-key delimiter]
  (let [decode-fn (if (string/blank? delimiter)
                    extract-list-object-keys
                    extract-list-object-common-prefixes)]
    (filter (comp not string/blank?)
            (-> (if (string/blank? delimiter)
                  (list-objects aws-http location-key)
                  (list-objects aws-http location-key delimiter))
                (:body)
                (xml/parse)
                (decode-fn location-key)))))

(deftype S3Location [aws-http bucket-name location-key]
  BucketLocation
  (put! [this obj]
    (put-object! aws-http location-key obj)
    this)

  (get-stream [_this]
    (:body (throw-io-exception-non-200 (get-object aws-http location-key))))

  (relative [_this relative-key]
    (->s3-location aws-http bucket-name (join-paths location-key relative-key)))

  (delete! [_this]
    (delete-object! aws-http location-key)
    nil)

  (child-keys [_this]
    (l-o aws-http
         location-key
         "/"))

  (descendant-keys [_this]
    (l-o aws-http location-key nil))

  (has-data? [_this]
    (= 200 (:status (get-object aws-http location-key))))

  (location-key [_this]
    location-key)

  (uri [_this]
    (URI. (str "s3://" bucket-name "/" location-key)))

  Object
  (toString [this] (str (uri this))))

(defn ->s3-location
  [aws-cli bucket-name location-key]
  (S3Location. aws-cli bucket-name (strip-slashes location-key)))

(deftype S3Bucket [aws-http bucket-name]
  BucketLocation
  (relative [_this relative-key]
    (->s3-location aws-http bucket-name relative-key))

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
    (l-o aws-http "" "/"))

  (descendant-keys [_this]
    (l-o aws-http "" nil))

  (location-key [_this]
    nil)

  (uri [_this]
    (URI. (str "s3://" bucket-name)))

  Object
  (toString [this] (str (uri this))))

(defn ->s3-bucket
  [{:keys [bucket-name access-key secret-key] :as _opts}]
  (assert bucket-name)
  (assert access-key)
  (assert secret-key)
  (->S3Bucket (->s3-http bucket-name access-key secret-key) bucket-name))
