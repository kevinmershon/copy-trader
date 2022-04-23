(ns copy-trader.exchange.traders
  (:require
   [clojure.tools.logging :as log]
   [copy-trader.cache :as cache]
   [copy-trader.config :refer [config]]
   [copy-trader.core :as core]
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
