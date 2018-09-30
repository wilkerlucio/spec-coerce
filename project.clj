(defproject spec-coerce "1.0.0-alpha7"
  :description "Coerce from specs"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[lein-doo "0.1.7"]]

  :dependencies [[com.wsscode/spec-inspec "1.0.0-alpha2"]]

  :source-paths ["src"]
  :test-paths ["test"]

  :jvm-opts ["-Duser.timezone=GMT"]

  :cljsbuild {:builds [{:id           "test-build"
                        :source-paths ["src" "test"]
                        :compiler     {:output-to     "out/testable.js"
                                       :main          'spec-coerce.cljs-test-runner
                                       :target        :nodejs
                                       :optimizations :none}}]}

  :profiles {:dev  {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [org.clojure/clojure "1.9.0"]
                                   [org.clojure/clojurescript "1.9.946"]]}

             :test {:dependencies [[org.clojure/clojure "1.9.0"]]}})
