(defproject jira-scraper "0.1.0-SNAPSHOT"
  :description "Library of functions for interpreting and manipulating data pulled from JIRA using the REST API"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.2"]
                 [clj-time "0.7.0"]
                 [org.clojure/tools.trace "0.7.5"]
                 [incanter "1.2.3-SNAPSHOT"]
                 [clout "1.2.0"]
                 [compojure "1.1.8"]
                 [cheshire "5.3.1"]
                 [org.clojure/core.cache "0.6.4"]
                 ]
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler jira-scraper.handler/app}
  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [midje "1.5.0"]]}}
  )
