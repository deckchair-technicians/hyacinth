(ns hyacinth.impl.ftp
  (:require [clj-ssh.ssh :as ssh]
            [clj-ssh.cli :as cli]

            [clojure.java.io :as io]
            [clojure.zip :as zip]
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

(defn exists? [ftp path]
  (try
    (ftp :stat path)
    true
    (catch Throwable t
      (if (= ChannelSftp/SSH_FX_NO_SUCH_FILE (.id t))
        false
        (throw t)))))

(defn is-directory? [ftp path]
  (try
    (.isDir (ftp :stat path))
    (catch Exception e
      (throw (ex-info (str "Problem checking is-directory? '" path "' " (.getMessage e)) (when (instance? SftpException e)
                                                                                          {:id (.id e)}) e)))))

(defn parts [path]
  (if (parent path)
    (conj (parts (parent path)) path)
    [path]))

(defn mkdirs [ftp dir]
  (let [parts (parts dir)]
    (doseq [part parts]
      (when-not (exists? ftp part)
        (ftp :mkdir part)))))

(defn ls-entries->keys [ls-entries]
  (->> ls-entries
       (map #(.getFilename %))
       (remove #(#{"." ".."} %))))

(defn get-child-keys [ftp path]
  (if path
    (when (is-directory? ftp path)
      (ls-entries->keys (ftp :ls path)))
    (ls-entries->keys (ftp :ls))))

(defn get-descendant-keys [ftp root]
  (when (is-directory? ftp root)
    (let [entries (->> (if root
                         (ftp :ls root)
                         (ftp :ls))
                       (remove #(#{"." ".."} (filename (.getFilename %)))))]
      (->> entries
           (mapcat (fn [entry]
                     (let [path (join-paths root (.getFilename entry))]
                       (if (.isDir (.getAttrs entry))
                         (cons path
                               (map #(join-paths path %) (get-descendant-keys ftp path)))
                         [path]))))
           (map #(relative-path root %)))
      )))

; Types
; ======================================================

(declare ->ftp-location)
(deftype FtpLocation [ftp location-key]
  BucketLocation
  (put! [this obj]
    (with-temp-dir
      [dir "hyacinth"]
      (let [file (to-file dir obj)]
        (when-let [parent-dir (parent location-key)]
          (mkdirs ftp parent-dir))
        (ftp :put file location-key)
        this)))

  (delete! [this]
    (try
      (if (is-directory? ftp location-key)
        (ftp :rmdir location-key)
        (ftp :rm location-key))
      (catch Exception e
        (if (= ChannelSftp/SSH_FX_NO_SUCH_FILE (:id (ex-data e)))
          nil
          (throw e))))
    nil)

  (get-stream [this]
    (with-temp-dir
      [dir "hyacinth"]
      (let [file (File. dir "forstreaming")]
        (try
          (ftp :get location-key (.getAbsolutePath file))
          (catch Throwable t
            (throw (IOException. (str "Could not get " location-key) t))))

        (io/input-stream file))))

  (child-keys [this]
    (get-child-keys ftp location-key))

  (descendant-keys [this]
    (get-descendant-keys ftp location-key))

  (relative [this relative-key]
    (assert (string? relative-key) (str "class:" (class relative-key)
                                        " value: " relative-key))

    (->ftp-location ftp (join-paths location-key relative-key)))

  (has-data? [this]
    (and (exists? ftp location-key)
         (not (is-directory? ftp location-key))))

  (location-key [this]
    location-key)

  Object
  (toString [this] (str "Ftplocation '" location-key "'")))

(defn ->ftp-location
  [ftp location-key]
  (FtpLocation. ftp location-key))

(deftype FtpBucket [ftp root]
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
    (get-descendant-keys ftp nil))

  (child-keys [this]
    (get-child-keys ftp root))

  (relative [this relative-key]
    (->ftp-location ftp (if root
                          (join-paths root relative-key)
                          relative-key)))
  (location-key [this]
    nil)

  Object
  (toString [this] "FtpBucket"))

(defn ->ssh-ftp [channel]
  (fn [& args]
    (apply ssh/sftp channel {} args)))

(defn ->cli-ftp [{:keys [port hostname username password session-options]
                  :or {session-options {:strict-host-key-checking :no}
                       port 22}}]
  (fn [& args]
    (cli/with-default-session-options session-options
      (apply cli/sftp
             hostname
             (concat
               args
               [:username username
                :password password
                :port     port])))))

(defn ->ftp-bucket
  "Either pass in com.jcraft.jsch.Channel, or a config map of:

  {:hostname  \"localhost\"
   :username  \"username\"
   :password  \"password\"}

   Optionally, you can specify clj-ssh :session-options.
   By default, session-options is {:strict-host-key-checking :no}

   Optionally, you can specify clj-ssh :session-options.
"
  [config-or-channel & {:keys [root]
                        :or {root nil}}]
  (if (map? config-or-channel)
    (FtpBucket. (->cli-ftp config-or-channel) root)
    (FtpBucket. (->ssh-ftp config-or-channel) root)))
