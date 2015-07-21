(ns hyacinth.impl.file
  (:require [me.raynes.fs :as fs]

            [clojure.java.io :as io]

            [clojure.string :as s]

            [hyacinth
             [protocol :refer :all]
             [util :refer [join-paths]]])
  (:import [java.io File]
           [java.net URI]))

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
(deftype FileLocation [file location-key]
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

    (FileLocation. (fs/file file relative-key) (join-paths location-key relative-key)))

  (has-data? [this]
    (and (not (fs/directory? file))
         (fs/exists? file)))

  (location-key [this]
    location-key)

  Object
  (toString [this] (str "FileLocation '" (.getAbsolutePath file) "'")))

(defn ->file-location
  [data-dir file-key]
  (assert (fs/directory? data-dir) (str "Not a directory " (.getAbsolutePath data-dir)))

  (let [file (fs/file data-dir file-key)]
    (FileLocation. file file-key)))

(deftype FileBucket [data-dir]
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
    (get-descendant-keys data-dir))

  (child-keys [this]
    (get-child-keys data-dir))

  (relative [this relative-key]
    (->file-location data-dir relative-key))

  (location-key [this]
    nil)

  Object
  (toString [this] (str "FileBucket '" (.getAbsolutePath data-dir) "'")))

(defn ->file-bucket
  [data-dir]
  (let [data-dir (fs/file data-dir)]
    (assert (fs/directory? data-dir) (str "Not a directory " (.getAbsolutePath data-dir)))
    (FileBucket. data-dir)))
