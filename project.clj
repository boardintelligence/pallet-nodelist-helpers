(defproject boardintelligence/pallet-nodelist-helpers "0.1.0-SNAPSHOT"
  :description "Library to help working with Pallet nodelist compute service"
  :url "https://github.com/boardintelligence/pallet-nodelist-helpers"
  :license {:name "MIT"
            :url "http://boardintelligence.mit-license.org"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.palletops/pallet "0.8.0-RC.1"]
                 [ch.qos.logback/logback-classic "1.0.7"]]

  :profiles {:dev {:plugins [[com.palletops/pallet-lein "0.8.0-alpha.1"]]}})
