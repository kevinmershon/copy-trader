(ns copy-trader.exchange.traders
  (:require
   [clojure.tools.logging :as log]
   [copy-trader.cache :as cache]
   [copy-trader.config :refer [config]]
   [copy-trader.core :as core]
   [copy-trader.exchange.trader :as trader]
   [copy-trader.exchange.alpaca.trader]))

(defn load-traders!
  []
  (try
    (swap! core/state assoc :traders
           (mapv (comp atom cache/with-orders-and-positions)
                 (:traders (config))))
    :ok
    (catch Throwable t
      (log/error t)
      (.getMessage t))))

(defn all-traders
  []
  (:traders @core/state))

(defn dispatch-trade!
  [{:keys [asset-type _symbol _direction _order-type _price _stop-loss _percentage] :as trade-msg}]
  (let [traders (->> (:traders @core/state)
                     (filter #(some #{asset-type} (:assets (deref %)))))]
    (doseq [trader traders]
      (let [{:keys [exchange] :as trader-map} @trader]
        (trader/on-trade trader-map (assoc trade-msg :exchange exchange))))))
