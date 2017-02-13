(defproject janus :lein-v
  :description "A rethink of Clojure routing"
  :url "https://github.com/cch1/janus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.roomkey/lein-v "6.1.0"]]
  :release-tasks [["vcs" "assert-committed"]
                  ["v" "update"]
                  ["vcs" "push"]
                  ["deploy" "clojars"]]
  :min-lein-version "2.5.2"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.match "0.2.2"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.0"]]}})
