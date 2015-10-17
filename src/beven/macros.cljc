(ns beven.macros)

(defmacro register-simple-sub [k]
  `(re-frame.core/register-sub
    ~k (fn [db# _#] (reagent.ratom/reaction (~k (deref db#))))))
