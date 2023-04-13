(ns copy-trader.exchange.trader
  (:refer-clojure :exclude [symbol])
  (:require
   [clojure.tools.logging :as log]))

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
  (fn [trader-map {:keys [exchange symbol direction order-type _price _stop-loss _percentage]}]
    {:pre [(map? trader-map) (string? exchange) (string? symbol)
           (string? direction) (string? order-type)]}
    [exchange direction order-type]))

(defmethod on-trade :default
  [_ _]
  :not-implemented)

(defmulti with-authorization
  (fn [{:keys [exchange] :as _trader-map} _code]
    exchange))

(defmethod with-authorization :default
  [_ _]
  :not-implemented)

(defn active-positions
  [{:keys [open-positions] :as _trader-map}]
  (reduce-kv
   (fn [m symbol {:keys [volume]}]
     (if (pos? volume)
       (assoc m symbol volume)
       m))
   {}
   open-positions))

(defn compute-position-size
  [{:keys [balance-usd leverage max_positions] :as trader-map}]
  (let [active                (active-positions trader-map)
        active-position-count (->> active keys count)
        position-size         (if (< active-position-count max_positions)
                                (/ (* balance-usd leverage)
                                   max_positions)
                                0.0)]
    (log/info "Computed position size" {:balance-usd   balance-usd
                                        :leverage      leverage
                                        :max-positions max_positions
                                        :position-size position-size})
    ;; return
    position-size))

(defn compute-volume
  [{:keys [risk leverage max_positions] :as trader-map} price high low]
  (let [diff               (- high low)
        balance-usd        (/ (* (:balance-usd trader-map) leverage)
                              max_positions)
        ;; use a minimum of the capital allocated, or don't take the trade
        min-usage          (/ (* balance-usd 0.20)
                              price)
        max-usage          balance-usd
        ;; we never want to risk more than our tolerance
        max-risk           (* risk max-usage)
        max-trade-size     (if (pos? diff)
                             (/ max-risk diff)
                             0.0)
        ;; this is the maximum amount we COULD buy
        max-purchase-power (/ max-usage price)
        ;; but this is the max we CHOOSE to buy
        desired-volume     (min max-purchase-power max-trade-size)]
    ;; don't take the trade unless we're meeting minimum volume requirements
    (if (> desired-volume min-usage)
      desired-volume
      0.0)))
