(ns hyacinth.impl.file
  (:require [me.raynes.fs :as fs]

            [clojure.java.io :as io]

            [clojure.string :as s]

            [hyacinth
             [protocol :refer :all]
             [util :refer [join-paths strip-slashes]]])
  (:import [java.io File]
           [java.net URI]
           [java.nio.file Path Paths]))

(defn path [fragment1 & fragments]
  (Paths/get fragment1 (into-array String fragments)))


(defn get-child-keys [dir]
  (or (fs/list-dir dir) []))

(defn relative-path [base other]
  (.getPath
    (.relativize (.toURI (fs/file base))
                 (.toURI (fs/file other)))))

(defn get-descendant-keys [root]
  (->> root
       (fs/walk (fn [parent _ files]
                  (->> files
                       (map #(fs/file parent %))
                       (map #(relative-path root %)))))
       (mapcat identity)))

(declare ->file-location)
(deftype FileLocation [bucket-dir file location-key]
  BucketLocation
  (put! [this obj]
    (fs/mkdirs (fs/parent file))
    (io/copy (coerce-to-streamable obj) file)
    this)

  (delete! [this]
    (fs/delete-dir file)
    nil)

  (get-stream [this]
    (io/input-stream file))

  (child-keys [this]
    (get-child-keys file))

  (descendant-keys [this]
    (get-descendant-keys file))

  (relative [this relative-key]
    (assert (string? relative-key) (str "class:" (class relative-key)
                                        " value: " relative-key))

    (->file-location bucket-dir (join-paths location-key relative-key)))

  (has-data? [this]
    (and (not (fs/directory? file))
         (fs/exists? file)))

  (location-key [this]
    location-key)

  (uri [this]
    (URI. (str "file://" (.getName bucket-dir) "/" location-key)))

  Object
  (toString [this] (str "FileLocation '" (.getAbsolutePath file) "'")))

(defn ->file-location
  [bucket-dir file-key]
  (let [file-key (strip-slashes file-key)
        bucket-path (.toPath bucket-dir)]

    (assert (fs/directory? bucket-dir) (str "Not a directory " (.getAbsolutePath bucket-dir)))
    (assert (.startsWith (.normalize (.resolve ^Path (.toPath bucket-dir) (path file-key))) bucket-path)
            (str "File key '" file-key "' is not accessible. If you have specified a path including ../ then you have gone above the bucket"))

    (let [file (fs/file bucket-dir file-key)]
      (FileLocation. bucket-dir file file-key))))

(deftype FileBucket [bucket-dir]
  BucketLocation
  (put! [this obj]
    (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

  (delete! [this]
    (throw (UnsupportedOperationException. "Can't delete buckets")))

  (get-stream [this]
    (throw (UnsupportedOperationException. "Can't put data directly to a bucket- specify a descendant")))

  (has-data? [this]
    false)

  (descendant-keys [this]
    (get-descendant-keys bucket-dir))

  (child-keys [this]
    (get-child-keys bucket-dir))

  (relative [this relative-key]
    (->file-location bucket-dir relative-key))

  (location-key [this]
    nil)

  (uri [this]
    (URI. (str "file://" (.getName bucket-dir))))

  Object
  (toString [this] (str "FileBucket '" (.getAbsolutePath bucket-dir) "'")))

(defn ->file-bucket
  [data-dir ^String bucket-name]
  (let [data-dir   (fs/file data-dir)
        bucket-dir (File. data-dir bucket-name)]

    (assert (fs/directory? data-dir) (str "Not a directory " (.getAbsolutePath data-dir)))
    (assert (= (.getFileName (path bucket-name))
               (path bucket-name))
            (str "Bucket name must be a valid file name '" bucket-name "'"))

    (FileBucket. (doto bucket-dir
                   (.mkdir)))))
