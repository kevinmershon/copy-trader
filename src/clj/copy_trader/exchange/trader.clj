(ns copy-trader.exchange.trader
  (:refer-clojure :exclude [symbol]))

(defmulti with-balance
  (fn [{:keys [exchange] :as _trader}]
    exchange))

(defmulti on-trade
  (fn [{:keys [exchange _symbol direction order-type _price _stop-loss _percentage]}]
    [exchange direction order-type]))

(defmethod on-trade :default
  [_]
  :not-implemented)
