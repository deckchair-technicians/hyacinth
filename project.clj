(defproject technology.theorem/hyacinth "DEV"

  :description "Bucket abstraction over S3, FTP, file, memory map"

  :url "https://bitbucket.org/TheoremTechnology/hyacinth"

  :plugins [[s3-wagon-private "1.1.2"]
            [lein-set-version "0.3.0" ]]

  :deploy-repositories [["releases" {:url "s3p://repo.theorem.technology/releases" :creds :gpg}]
                        ["snapshots" {:url "s3p://repo.theorem.technology/snapshots" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.0"]]}})
