(defproject technology.theorem/hyacinth "DEV"


  :repositories [["theorem-repo" ~(if (System/getenv "AWS_ACCESS_KEY_ID")
                                    {:url "s3p://theorem-repo/repo" :username :env :passphrase :env}
                                    {:url "s3p://theorem-repo/repo" :creds :gpg})]]

  :description "Bucket abstraction over S3, FTP, file, memory map"

  :plugins [[lein-maven-s3-wagon "0.2.4"]
            [lein-set-version "0.3.0" ]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.0"]]}})
