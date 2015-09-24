(ns hyacinth.aws.cli
  (:require [clojure.java
             [shell :as shell]
             [io :as io]]
            [hyacinth
             [util :refer [strip-slashes join-paths]]]
            [clojure.string :as s])
  (:import (clojure.lang ExceptionInfo)
           (java.io IOException File)))

(defn trim-after-first-slash [s]
  (s/replace s #"/.*" ""))


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
  (cp-up [this from-input-stream to-location-key])
  (cp-down [this from-location-key to-file])
  (rm [this location-key]))

(deftype BucketAwsCli [bucket-name region profile]
  AwsCli
  (ls [_ location-key]
    (aws-sh "ls" region profile (str "s3://" bucket-name "/" location-key) "--recursive"))

  (cp-up [_ from-input-stream to-location-key]
    (throw-no-such-key (str bucket-name "/" to-location-key)
                       (aws-cp region profile
                               from-input-stream
                               (str "s3://" (join-paths bucket-name to-location-key)))))

  (cp-down [_ from-location-key to-file]
    (let [s3-url (str "s3://" (join-paths bucket-name from-location-key))]
      (throw-no-such-key (str bucket-name "/" from-location-key)
                         (aws-cp region profile s3-url (.getAbsolutePath ^File to-file)))

      (io/input-stream to-file)))

  (rm [_ location-key]
    (let [s3-url (str "s3://" (join-paths bucket-name location-key))]
      ; TODO: This assert is gross.
      (assert (= 0 (:exit (aws-sh "rm" region profile s3-url)))))))

(defn list-descendants
  [aws-cli prefix]
  (let [{:keys [out]} (ls aws-cli prefix)]
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
