(defproject clj-fluiddb "0.0.1-SNAPSHOT"
  :description "FluidDb Client Library"
  :url "http://github.com/hugoduncan/clj-fluiddb"
  :dependencies [[org.clojure/clojure "1.1.0"]
		 [org.clojure/clojure-contrib "1.1.0"]]
  :dev-dependencies [[leiningen/lein-swank "1.0.0-SNAPSHOT"]
                     [autodoc "0.7.0"]]
  :repositories { "build.clojure.org" "http://build.clojure.org/releases/" }
  :autodoc {:name "clj-fluiddb"
	    :description "A FluidDb client library."
	    :copyright "Copyright Hugo Duncan 2010. All rights reserved."
	    :web-src-dir "http://github.com/hugoduncan/clj-fluiddb/blob/"
	    :web-home "http://hugoduncan.github.com/clj-fluiddb/" })
