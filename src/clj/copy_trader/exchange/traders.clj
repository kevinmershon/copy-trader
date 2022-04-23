(ns copy-trader.exchange.traders
  (:require
   [cheshire.core :refer [parse-string]]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [copy-trader.cache :as cache]
   [copy-trader.core :as core]
   [copy-trader.exchange.alpaca.trader]))

(defn load-traders!
  []
  (try
    (swap! core/state assoc :traders
           (mapv (comp atom cache/with-orders-and-positions)
                 (-> (io/file "config.json")
                     slurp
                     (parse-string true)
                     :traders)))
    :ok
    (catch Throwable t
      (log/error t)
      (.getMessage t))))

(defn all-traders
  []
  (:traders @core/state))
