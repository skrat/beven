(ns beven.views
  (:require [beven.model :as m]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [re-com.core :as com]
            [goog.string :as gstring]))

(def last-input-query
  ".item-list > div:last-child input")

(defn focus-last-item []
  (js/setTimeout
   (fn [] (->> last-input-query
               (.querySelector js/document)
               (.focus))) 10))

(defn format-amount [x]
  (if (> x 0) (gstring/format "%.2f" x) "-"))

(defn format-person [x]
  (if (= 0 (count x)) "(anon)" x))

(defn should-add-item [e]
  (or (= 13 (.-keyCode e))          ;; enter
      (and (=  9 (.-keyCode e))     ;; tab
           (not (.-shiftKey e)))))  ;; ...but no shift

(defn item [x]
  [com/h-box
   :class "item"
   :children
   [[com/input-text
     :model (:person x)
     :width "35%"
     :placeholder "person"
     :on-change #(dispatch [:save-item (assoc x :person %)])
     :attr {:list "persons"}]
    [com/input-text
     :model (:label x)
     :width "40%"
     :placeholder "label"
     :on-change #(dispatch [:save-item (assoc x :label %)])]
    [com/input-text
     :width "20%"
     :class "amount"
     :model (str (:amount x))
     :placeholder "amount"
     :validation-regex #"^\d+$"
     :on-change #(dispatch [:save-item (assoc x :amount (js/parseInt %))])
     :attr {:on-key-down #(when (should-add-item %)
                            (dispatch-sync [:add])
                            (focus-last-item))}]
    [com/md-icon-button
     :class "kill"
     :md-icon-name "zmdi-close"
     :on-click #(dispatch [:kill-item x])]]])

(defn item-list []
  (let [items  (subscribe [:items])
        saved? (subscribe [:saved?])]
    (fn []
      [:div.items
       [:datalist {:id "persons"}
        (for [p (set (map :person @items))]
          ^{:key p} [:option {:value p}])]
       [com/v-box
        :gap "12px"
        :class "item-list"
        :children
        (for [x @items]
          ^{:key (:id x)} [item x])]
       [com/h-box
        :class "actions"
        :width "95%"
        :children
        [(case @saved?
           false [com/button
                  :label [:span "Save"]
                  :on-click #(dispatch [:save nil])]
           nil   [com/button
                  :label [:span "Save" [:i.glyphicon.glyphicon-refresh.spinning]]
                  :disabled? true]
           true  [com/button
                  :label [:span "Share"]
                  :on-click #(dispatch [:share true])])
         [com/gap
          :size "100%"]
         [com/button
          :label "Add item"
          :class "btn-primary"
          :on-click #(dispatch [:add])]]]])))


(defn sum-total []
  (let [total (subscribe [:total])
        items (subscribe [:items])]
    (fn []
      (let [t          (deref total)
            by-person  (m/spent-by-person @items)
            per-person (/ t (count by-person))
            solution   (m/solve @items)
            payments   (for [[from to-map] solution
                             [to sum] to-map
                             :when (not (zero? sum))
                             :let [k (str from "-" to)]]
                         [k from to sum])]
        [com/v-box
         :class "sum"
         :children
         [[:table.table
           [:tr
            [:th "Total costs:"] [:td [:strong (format-amount t)]]]
           [:tr
            [:th "Per person:"]  [:td [:strong (format-amount per-person)]]]]
          [:ul.list-group
           (if (empty? payments)
             [:li.list-group-item "No payments necessary, we're even!"]
             (for [[k from to sum] payments]
               ^{:key k}
               [:li.list-group-item
                [:strong (format-person from)]
                " pays "
                [:strong.badge (format-amount sum)]
                " to "
                [:strong (format-person to)]]))]]]))))

(defn sharing []
  (let [sharing? (subscribe [:sharing?])]
    (fn []
      (if @sharing?
        [com/modal-panel
         :child [:section
                 [:h5 "Share the bill"]]
         :backdrop-on-click #(dispatch [:share false])]))))

(defn root []
  [:section.container
   [:header.header.clearfix
    [:h3.text-muted "Big payback"]]
   [item-list]
   [sum-total]
   [sharing]])

