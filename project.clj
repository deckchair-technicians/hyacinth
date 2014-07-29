(defproject hyacinth "0.1.0-SNAPSHOT"

  :description "Bucket abstraction over S3, FTP, file, memory map"

  :url "https://bitbucket.org/theshoreditchproject/hyacinth"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[s3-wagon-private "1.1.2"]
            [lein-set-version "0.3.0" ]]

  :repositories [["callisto" "s3p://callisto-artifacts/releases/"]]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [amazonica "0.1.28"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.6.2"]
                                  [midje-junit-formatter "0.1.0-SNAPSHOT"]]
                   :plugins [[lein-midje "3.1.0"]]}})
