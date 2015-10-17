(defproject beven "0.1.0-SNAPSHOT"
  :description "Claculate trip costs for each person"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [prismatic/schema "1.0.1"]
                 [re-frame "0.4.1"]
                 [reagent "0.5.1"]
                 [re-com "0.6.2"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.3.5"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src"]
     :figwheel {:on-jsload "beven.core/on-js-reload"}
     :compiler {:main beven.core
                :asset-path "js/compiled/out"
                :output-to "resources/public/js/compiled/beven.js"
                :output-dir "resources/public/js/compiled/out"
                :source-map-timestamp true}}
    {:id "min"
     :source-paths ["src"]
     :compiler {:output-to "resources/public/js/compiled/beven.js"
                :main beven.core
                :optimizations :advanced
                :pretty-print false}}]}

  :figwheel
  {:css-dirs ["resources/public/css"]
   :nrepl-port 7888})
