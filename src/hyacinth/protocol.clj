(ns hyacinth.protocol
  (:import (java.io Reader InputStream File ByteArrayInputStream)))

(defmulti coerce-to-streamable class)

(defmethod coerce-to-streamable String [s]
  (coerce-to-streamable (.getBytes s)))

(defmethod coerce-to-streamable (Class/forName "[B") [b]
  (ByteArrayInputStream. b))

(defmethod coerce-to-streamable File [f] f)

(defmethod coerce-to-streamable InputStream [s] s)

(defmethod coerce-to-streamable Reader [r] r)

(defprotocol BucketLocation
  (put! [this obj])
  (delete! [this])
  (get-stream [this])
  (has-data? [this])
  (child-keys [this])
  (descendant-keys [this])
  (relative [this key])
  (location-key [this])
  (uri [this]))

(defn descendant-locations [location]
  (map #(relative location %)
       (descendant-keys location)))

(defn children [location]
  (map #(relative location %) (child-keys location)))

(defn copy-location [from to]
  (put! to (get-stream from)))

(defn recursive-delete! [location]
  (doseq [child (children location)]
    (recursive-delete! child))
  (delete! location))
