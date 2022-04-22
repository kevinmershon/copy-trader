(ns copy-trader.system
  (:require
   [copy-trader.exchange.traders :as traders]
   [copy-trader.core :as core]
   [copy-trader.scheduling.scheduler :as scheduler]
   [copy-trader.scheduling.jobs :as jobs]
   [copy-trader.websocket]))

(defn halt
  []
  ;; FIXME
  (comment "disconnect websocket clients")
  (scheduler/halt!)
  (reset! core/state {:is-running? false})
  :halted)

(defn go
  []
  (traders/load-traders!)
  (scheduler/start!)
  (jobs/schedule-jobs!)
  (swap! core/state assoc :is-running? true)
  ;; FIXME
  (comment "fetch all balances, orders, and positions")
  (comment "connect websocket clients")

  (println "Running. Use (halt) to stop.")
  :running)
