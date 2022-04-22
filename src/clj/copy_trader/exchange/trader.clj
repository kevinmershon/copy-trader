(ns copy-trader.exchange.trader
  (:refer-clojure :exclude [symbol]))

(defmulti with-balance
  (fn [{:keys [exchange] :as _trader}]
    exchange))

(defmulti with-orders
  (fn [{:keys [exchange] :as _trader}]
    exchange))

(defmulti with-positions
  (fn [{:keys [exchange] :as _trader}]
    exchange))

(defmulti on-trade
  (fn [{:keys [exchange _symbol direction order-type _price _stop-loss _percentage]}]
    [exchange direction order-type]))

(defmethod on-trade :default
  [_]
  :not-implemented)

(defn compute-volume
  [{:keys [balance-usd risk] :as _trader-map} price high low]
  (let [diff                 (- high low)
        ;; use a minimum of the capital allocated, or don't take the trade
        min-usage            (/ (* balance-usd 0.20)
                                price)
        max-usage            balance-usd
        ;; we never want to risk more than our tolerance
        max-risk             (* risk max-usage)
        max-trade-size       (if (pos? diff)
                               (/ max-risk diff)
                               0.0)
        ;; this is the maximum amount we COULD buy
        max-purchase-power   (/ max-usage price)
        ;; but this is the max we CHOOSE to buy
        desired-volume       (min max-purchase-power max-trade-size)]
    ;; don't take the trade unless we're meeting minimum volume requirements
    (if (> desired-volume min-usage)
      desired-volume
      0.0)))
