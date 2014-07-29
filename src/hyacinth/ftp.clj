(ns hyacinth.ftp
  (:require [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clj-ssh.ssh :refer [sftp]]
            [me.raynes.fs :as fs]
            [clojure.string :as s]
            [hyacinth
             [util :refer :all]
             [protocol :refer :all]])
  (:import [java.io File IOException]
           [java.net URI]
           [com.jcraft.jsch ChannelSftp SftpException SftpATTRS ChannelSftp$LsEntry]
           [java.util.regex Pattern]))

; Utils
; ======================================================
(defn relative-path [base other]
  (-> other
      (s/replace (re-pattern (str "^" (Pattern/quote base))) "")
      (s/replace #"^/" "")))

(defn exists? [channel path]
  (try
    (sftp channel {} :stat path)
    true
    (catch Throwable t
      (if (= ChannelSftp/SSH_FX_NO_SUCH_FILE (.id t))
        false
        (throw t)))))

(defn is-directory? [channel path]
  (.isDir (sftp channel {} :stat path)))

(defn parts [path]
  (if (parent path)
    (conj (parts (parent path)) path)
    [path]))

(defn mkdirs [channel dir]
  (let [parts (parts dir)]
    (doseq [part parts]
      (when-not (exists? channel part)
        (sftp channel {} :mkdir part)))))

(defn get-child-keys [channel path]
  (if path
    (when (is-directory? channel path)
      (->> (sftp channel {} :ls path)
           (map #(.getFilename %))
           (remove #(#{"." ".."} %))))
    (map #(.getFilename %) (sftp channel {} :ls))))

(defn get-descendant-keys [channel root]
  (when (is-directory? channel root)
    (let [entries (->> (if root
                         (sftp channel {} :ls root)
                         (sftp channel {} :ls))
                       (remove #(#{"." ".."} (filename (.getFilename %)))))]
      (->> entries
           (mapcat (fn [entry]
                     (let [path (->path root (.getFilename entry))]
                       (if (.isDir (.getAttrs entry))
                         (cons path
                               (map #(->path path %) (get-descendant-keys channel path)))
                         [path]))))
           (map #(relative-path root %)))
      )))

; Types
; ======================================================

(declare ->ftp-location)
(deftype FtpLocation [channel location-key]
  BucketLocation
  (put! [this obj]
    (with-temp-dir
      [dir "hyacinth"]
      (let [file (to-file dir obj)]
        (when-let [parent-dir (parent location-key)]
          (mkdirs channel parent-dir))
        (sftp channel {} :put file location-key)
        this)))

  (delete! [this]
    (try
      (if (is-directory? channel location-key)
        (sftp channel {} :rmdir location-key)
        (sftp channel {} :rm location-key))
      (catch SftpException e
        (if (= ChannelSftp/SSH_FX_NO_SUCH_FILE (.id e))
          nil
          (throw e))))
    nil)

  (get-stream [this]
    (with-temp-dir
      [dir "hyacinth"]
      (let [file (File. dir "forstreaming")]
        (try
          (sftp channel {} :get location-key (.getAbsolutePath file))
          (catch Throwable t
            (throw (IOException. (str "Could not get " location-key) t))))

        (io/input-stream file))))

  (child-keys [this]
    (get-child-keys channel location-key))

  (descendant-keys [this]
    (get-descendant-keys channel location-key))

  (relative [this relative-key]
    (assert (string? relative-key) (str "class:" (class relative-key)
                                        " value: " relative-key))

    (->ftp-location channel (->path location-key relative-key)))

  (has-data? [this]
    (and (exists? channel location-key)
         (not (is-directory? channel location-key))))

  Object
  (toString [this] (str "Ftplocation '" location-key "'")))

(defn ->ftp-location
  [channel location-key]
  (FtpLocation. channel location-key))

(deftype FtpBucket [channel]
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
    (get-descendant-keys channel nil))

  (child-keys [this]
    (get-child-keys channel ""))

  (relative [this relative-key]
    (->ftp-location channel relative-key))

  Object
  (toString [this] "FtpBucket"))

(defn ->ftp-bucket
  [channel]
  (FtpBucket. channel))
