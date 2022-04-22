(ns copy-trader.system
  (:require
   [copy-trader.core :as core]
   [copy-trader.exchange.alpaca.trader]
   [copy-trader.websocket]))

(defn halt
  []
  ;; FIXME
  (comment "disconnect websocket clients")
  (swap! core/state assoc :is-running? false)
  :halted)

(defn go
  []
  (swap! core/state assoc :is-running? true)
  ;; FIXME
  (comment "fetch all balances, orders, and positions")
  (comment "connect websocket clients")

  (println "Running. Use (halt) to stop.")
  :running)
