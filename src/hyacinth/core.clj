(ns hyacinth.core
  (:require [clojure.string :as s]
            [hyacinth
             [util :refer [strip-slashes]]
             [protocol :as h]]
            [hyacinth.impl
             [aws-cli :refer [->s3-bucket]]
             [file :refer [->file-bucket]]
             [memory :refer [->memory-bucket]]
             ])
  (:import [java.net URI]))

(defn uri->memory-location [memory-bucket-atom uri]
  (let [bucket (->memory-bucket memory-bucket-atom (.getHost uri))]
    (if (s/blank? (.getPath uri))
      bucket
      (h/relative bucket (.getPath uri)))))

(defn uri->s3-location [uri]
  (let [bucket (->s3-bucket (.getHost uri))]
    (if (s/blank? (.getPath uri))
      bucket
      (h/relative bucket (.getPath uri)))))

(defn uri->file-location [file-bucket-root uri]
  (when (nil? file-bucket-root)
    (throw (IllegalStateException. "You must specify :file-bucket-root in opts in order to use file buckets")))

  (if (s/blank? (.getPath uri))
    (throw (IllegalAccessException. (str "You must specify a path in file uri " uri))))

  (let [bucket (->file-bucket file-bucket-root (.getHost uri))]
    (h/relative bucket (.getPath uri))))

(defn ->uri->location [& {:keys [memory-bucket-atom
                                  file-bucket-root]
                           :or   {memory-bucket-atom (atom {})}}]
  (fn [^URI uri]
    (case (s/lower-case (.getScheme uri))
      "mem" (uri->memory-location memory-bucket-atom uri)
      "s3" (uri->s3-location uri)
      "file" (uri->file-location file-bucket-root uri)

      (throw (UnsupportedOperationException. (str "I don't understand protocol '" (.getScheme uri) "' in uri " uri))))))
