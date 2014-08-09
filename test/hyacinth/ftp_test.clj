(ns hyacinth.ftp-test
  (:require [midje.sweet :refer :all]
            [clj-ssh.ssh :refer :all]
            [hyacinth.contract :refer :all]

            [clojure.java.io :as io]
            [hyacinth.protocol :refer :all]
            [hyacinth.ftp :refer :all])
  (:import [java.util Properties Map$Entry]))

(defn load-properties [url]
  (doto (Properties.)
    (.load (io/input-stream url))))

(defn prop-map [url]
  (reduce (fn [xs [k v]] (assoc xs (keyword k) v))
          {}
          (load-properties url)))

(def props-path "donotcheckin/ftp.properties")
(def props (delay
             (if-let [url (io/resource props-path)]
               (prop-map url)
               (println "FTP test not running- create"
                        (str "test/" props-path)
                        "to run test. This file should be in .gitignore. Double check it really is before commiting."))))

(when @props
  (facts "ftp buckets work with ftp using a defined Channel"
    (let [agent (ssh-agent {})]
      (let [session (session agent (:hostname @props) {:strict-host-key-checking :no
                                                      :username                 (:username @props)
                                                      :password                 (:password @props)})]
        (with-connection session
          (let [channel (ssh-sftp session)]
            (with-channel-connection channel
              (check-contract (->ftp-bucket channel))))))))

  (facts "ftp buckets work with cli ftp"
         (check-contract (->ftp-bucket @props))))
