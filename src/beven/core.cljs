(ns ^:figwheel-always beven.core
    (:require
     [beven.model]
     [beven.views :as v]
     [reagent.core :as reagent]
     [re-frame.core :refer [dispatch-sync]]))

(enable-console-print!)

(defn render []
  (reagent/render-component
   [v/root] (. js/document (getElementById "app"))))

(defn main []
  (dispatch-sync [:init])
  (render)
  (v/focus-last-item))

(defn on-js-reload []
  (render))

(defonce _ (main)) ;; ---
