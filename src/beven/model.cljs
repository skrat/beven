(ns beven.model
  (:require [beven.macros :refer-macros [register-simple-sub]]
            [schema.core :as s :include-macros true]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :refer [dispatch
                                   register-sub
                                   register-handler]]
            [cljs.reader :as r]))

(def pos-num
  "Positive number validtor"
  (s/pred pos? "positive number"))

(s/defrecord Item
    [id     :- s/Int
     label  :- s/Str
     person :- s/Str
     amount :- pos-num])

(def empty-item
  (Item. 0 "" "" 0))

(def empty-db
  "Basic schema for split costs database"
  {:items {0 empty-item}
   :item-id-counter 1
   :saved?   false
   :sharing? false})

;; Register subscription handlers
;;

(register-sub
 :items
 (fn [db _]
   (reaction (vals (:items @db)))))

(register-sub
 :total
 (fn [db _]
   (reaction
    (->> @db :items vals
         (transduce (map :amount) + 0)))))

(register-simple-sub :saved?)
(register-simple-sub :sharing?)

;; Initialize re-frame state with saved or empty-db
;;

(r/register-tag-parser! "beven.model.Item" map->Item)

(register-handler
 :init
 (fn [db _]
   (try
     (let [s (->> "#state"
                  (.querySelector js/document) (.-innerHTML)
                  (r/read-string))]
       (merge db empty-db s))
     (catch js/Error err
       (merge db empty-db)))))

(register-handler
 :add
 (fn [{id :item-id-counter :as db} _]
   (if (not= empty-item (-> db :items (get (dec id)) (assoc :id (:id empty-item))))
     (-> db
         (update :items assoc id (assoc empty-item :id id))
         (update :item-id-counter inc))
     db)))

(register-handler
 :save-item
 (fn [db [_ x]]
   (assoc-in db [:items (:id x)] x)))

(register-handler
 :kill-item
 (fn [db [_ x]]
   (update db :items dissoc (:id x))))

(register-handler
 :save
 (fn [db [_ x]]
   (print (pr-str db))
   (js/setTimeout #(dispatch [:save true]) 2000)
   (assoc db :saved? x)))

(register-handler
 :share
 (fn [db [_ sharing?]]
   (assoc db :sharing? sharing?)))

;; Solving settlement
;;

(defn spent-by-person
  "Calculate how much each person spent, returns map"
  [items]
  (->>
   (for [[p xs] (group-by :person items)]
     [p (transduce (map :amount) + 0 xs)])
   (into {})))

(defn map-matrix
  "Create unordered matrix made of maps, keys from `coll' and
  initialize with value `val'"
  [coll val]
  (into {} (for [x coll] [x (into {} (for [y coll] [y val]))])))

(defn balance
  "Calculate balance for each person"
  [items]
  (let [total      (transduce (map :amount) + 0 items)
        by-person  (spent-by-person items)
        per-person (/ total (count by-person))]
    (->> (for [[p sum] by-person]
           [p (- per-person sum)])
         (into {}))))

(defn filter-balance
  "Filter out zero balances"
  [balance]
  (->>
   balance
   (filter (comp not zero? second))
   (into {})))

(defn solve
  "Solve the settlement, return payment matrix"
  [items]
  (let [b (balance items)]
    (loop [balance b
           payments (map-matrix (keys b) 0)]
      (if (not (empty? balance))
        (let [;; find richest person
              [rp rb] (first (sort-by (comp - second) balance))
              ;; find poorest person
              [pp pb] (first (sort-by (comp + second) balance))
              ;; amount to transfer from richest to poorest
              t (min rb (- pb))]
          (if (not= rp pp)
            (recur
             (-> balance
                 (update rp - t)
                 (update pp + t)
                 (filter-balance))
             (-> payments
                 (update-in [rp pp] + t)))
            payments))
        ;; everyone's balance zeroed, return payment matrix
        payments))))
