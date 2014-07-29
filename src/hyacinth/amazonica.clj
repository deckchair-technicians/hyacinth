(ns hyacinth.amazonica
  (:require [clojure.java.io :as io]

            [clojure.string :as s]

            [amazonica.aws.s3 :as s3]

            [hyacinth.protocol :refer :all])

  (:import [java.io File IOException]
           [com.amazonaws.services.s3.model AmazonS3Exception]))

(defn replace-prefix [prefix]
  (let [pattern (re-pattern (str "^" (s/re-quote-replacement prefix) "/?(.*)"))]
    (fn [s]
      (assert (re-matches pattern s) (str pattern " does not match '" s "'"))
      (s/replace s pattern (fn [[_ x & _]] x)))))

(defn trim-after-first-slash [s]
  (s/replace s #"/.*" ""))

(defn list-child-keys [creds bucket-name prefix]
  (->> (s3/list-objects creds bucket-name prefix)
       :object-summaries
       (map :key)
       set))

(defn list-relative-keys [creds bucket-name location-key]
  (->> (list-child-keys creds bucket-name location-key)
       (map (replace-prefix location-key))
       (filter (comp not #{""}))))


(declare ->s3-location)
(deftype S3Location
  [creds bucket-name location-key]
  BucketLocation
  (put! [this obj]
    (s3/put-object creds
                   :bucket-name bucket-name
                   :key location-key
                   :input-stream (coerce-to-streamable obj))
    this)

  (delete! [this]
    (try
      (s3/delete-object creds bucket-name location-key)
      (catch AmazonS3Exception e
        (when (not= 404 (.getStatusCode e))
          (throw e))))
    (doseq [descendant (descendant-locations this)]
      (delete! descendant)))

  (get-stream [this]
    (try
      (:object-content (s3/get-object creds bucket-name location-key))
      (catch AmazonS3Exception e
        (if (= 404 (.getStatusCode e))
          (throw (IOException. (str "Not such location:" bucket-name "/" location-key) e))
          (throw e)))))

  (child-keys [this]
    (map trim-after-first-slash
         (list-relative-keys creds bucket-name location-key)))

  (descendant-keys [this]
    (list-relative-keys creds bucket-name location-key))

  (relative [this relative-key]
    (->s3-location creds bucket-name (.getPath (File. location-key relative-key))))

  (has-data? [this]
    (contains?
      (list-child-keys creds bucket-name location-key)
      location-key))

  Object
  (toString [this] (str "S3Location '" bucket-name "/" location-key "'")))

(defn ->s3-location [creds bucket-name location-key]
  (S3Location. creds bucket-name location-key))

(deftype S3Bucket [creds bucket-name]
  BucketLocation
  (put! [this obj]
    (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

  (delete! [this]
    (throw (UnsupportedOperationException. "Can't delete buckets")))

  (get-stream [this]
    (throw (UnsupportedOperationException. "Can't get data directly from a bucket- specify a descendant")))

  (has-data? [this]
    false)

  (child-keys [this]
    (list-child-keys creds bucket-name nil))

  (relative [this relative-key]
    (->s3-location creds bucket-name relative-key))

  Object
  (toString [this] (str "S3Bucket '" bucket-name "'")))

(defn ->s3-bucket
  [creds bucket-name]

  (S3Bucket. creds bucket-name))
