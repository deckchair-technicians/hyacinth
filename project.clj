(defproject technology.theorem/hyacinth "DEV"

  :repositories [["theorem-repo" {:url "s3://theorem-repo/repo"
                                  :username [:gpg :env/aws_access_key_id]
                                  :passphrase [:gpg :env/aws_secret_access_key]}]]

  :description "Bucket abstraction over S3, FTP, file, memory map"

  :plugins [[s3-wagon-private "1.2.0"]
            [lein-set-version "0.3.0" ]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.0"]]}})
