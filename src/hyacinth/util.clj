(ns hyacinth.util
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [hyacinth.protocol :refer [coerce-to-streamable]]
            [clojure.string :as s])
  (:import [java.io File]))

(defn to-file [dir obj]
  (if (instance? File obj)
    (.getAbsolutePath obj)
    (let [file (File. dir "forupload")]
      (io/copy (coerce-to-streamable obj) file)
      (.getAbsolutePath file))))

(defmacro with-temp-dir
  [[var prefix] & body]
  `(let [~var (fs/temp-dir ~prefix)]
     (try
       (do ~@body)
       (finally
         (fs/delete-dir ~var)))))

(defn dissoc-in*
  "Lifted from flatland.useful.map

  Dissociates a value in a nested associative structure, where ks is a sequence of keys and returns
  a new nested structure. If any resulting maps are empty, they will be removed from the new
  structure. This implementation was adapted from clojure.core.contrib, but the behavior is more
  correct if keys is empty."
  [m keys]
  (if-let [[k & ks] (seq keys)]
    (if-let [old (get m k)]
      (let [new (dissoc-in* old ks)]
        (if (seq new)
          (assoc m k new)
          (dissoc m k)))
      m)
    {}))


(defn join-paths [& paths]
  (reduce #(.getPath (File. %1 %2)) paths))

(defn join-path-forward-slash [^String path & paths]
  (reduce
    (fn [path-left path-right]
      (clojure.string/replace (str path-left "/" path-right) #"/+" "/"))
    path
    paths))

(defn parent [path]
  (.getParent (if (instance? File path)
                path
                (File. path))))

(defn filename [path]
  (.getName (File. path)))

(defn strip-slashes [s]
  (s/replace s #"^/|/$" ""))
