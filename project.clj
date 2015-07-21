(defproject com.callisto/hyacinth "0.1.0-SNAPSHOT"

  :description "Bucket abstraction over S3, FTP, file, memory map"

  :url "https://bitbucket.org/TheoremTechnology/hyacinth"

  :plugins [[s3-wagon-private "1.1.2"]
            [lein-set-version "0.3.0" ]]

  :deploy-repositories [["releases" {:url "s3p://repo.solo.com/releases" :creds :gpg}]
                        ["snapshots" {:url "s3p://repo.solo.com/snapshots" :creds :gpg}]]

  :repositories [["solo" ~(if (System/getenv "AWS_ACCESS_KEY_ID")
                            {:url "s3p://repo.solo.com/releases" :username :env :passphrase :env}
                            {:url "s3p://repo.solo.com/releases" :creds :gpg})]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.0"]]}})
