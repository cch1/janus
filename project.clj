(defproject janus :lein-v
  :description "A rethink of Clojure routing"
  :url "https://github.com/cch1/janus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[com.roomkey/lein-v "7.0.0"]
            [lein-doo "0.1.10"]
            [lein-cljsbuild "1.1.7"]]
  :middleware [lein-v.plugin/middleware]
  :release-tasks [["vcs" "assert-committed"]
                  ["v" "update"]
                  ["vcs" "push"]
                  ["deploy" "clojars"]]
  :min-lein-version "2.5.2"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/core.match "0.3.0"]]
  :managed-dependencies [[com.google.errorprone/error_prone_annotations "2.1.3"]
                         [com.google.code.findbugs/jsr305 "3.0.2"]
                         [commons-codec "1.11"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2" :exclusions [ring/ring-codec]]
                                  [com.bhauman/figwheel-main "0.2.0" :exclusions [org.clojure/clojurescript]]]}}
  :doo {:build "test"}
  :cljsbuild {:builds [{:id "test" ; https://lambdaisland.com/episodes/testing-clojurescript
                        :source-paths ["src" "test"]
                        :compiler {:output-to "resources/public/js/testable.js"
                                   :output-dir "target"
                                   :main janus.runner
                                   :optimizations :none}}]}
  :aliases {"omnitest" ^{:doc "Run Clojure and ClojureScript tests"} ["do" "test" ["doo" "phantom" "once"]]})
