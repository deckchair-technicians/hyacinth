(defproject hyacinth "0.1.0-SNAPSHOT"

  :description "FIXME: write description"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-time "0.6.0"]
                 [cheshire "5.3.0"]
                 [amazonica "0.1.28"]
                 [clj-ssh "0.5.9"]
                 [me.raynes/fs "1.4.5"]]

  :profiles {:dev {:dependencies [[midje "1.6.2"]]
                   :plugins [[lein-midje "3.1.0"]]}})
