(ns copy-trader.system
  (:require
   [copy-trader.exchange.traders :as traders]
   [copy-trader.core :as core]
   [copy-trader.scheduling.scheduler :as scheduler]
   [copy-trader.scheduling.jobs :as jobs]
   [copy-trader.server :as server]
   [copy-trader.websocket.client :as ws]))

(defn halt
  []
  (when (:is-running? @core/state)
    (ws/disconnect-clients!)
    (scheduler/halt!)
    (server/halt!)
    (reset! core/state {:is-running? false}))
  :halted)

(defn go
  []
  (when-not (:is-running? @core/state)
    (swap! core/state assoc :is-running? true)
    (traders/load-traders!)
    (scheduler/start!)
    (server/start!)
    (jobs/schedule-jobs!)
    (ws/connect-clients!))
  :running)
