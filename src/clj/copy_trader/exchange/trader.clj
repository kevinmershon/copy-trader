(ns copy-trader.exchange.trader
  (:refer-clojure :exclude [symbol]))

(defmulti on-trade
  (fn [{:keys [exchange _symbol direction order-type _price _stop-loss]}]
    [exchange direction order-type]))

(defmethod on-trade :default
  [_]
  :not-implemented)
