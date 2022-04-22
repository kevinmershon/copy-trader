(ns copy-trader.system
  (:require
   [copy-trader.exchange.traders :as traders]
   [copy-trader.core :as core]
   [copy-trader.websocket]))

(defn halt
  []
  ;; FIXME
  (comment "disconnect websocket clients")
  (reset! core/state {:is-running? false})
  :halted)

(defn go
  []
  (traders/load-traders!)
  (swap! core/state assoc :is-running? true)
  ;; FIXME
  (comment "fetch all balances, orders, and positions")
  (comment "connect websocket clients")

  (println "Running. Use (halt) to stop.")
  :running)
