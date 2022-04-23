(ns copy-trader.system
  (:require
   [copy-trader.exchange.traders :as traders]
   [copy-trader.core :as core]
   [copy-trader.scheduling.scheduler :as scheduler]
   [copy-trader.scheduling.jobs :as jobs]
   [copy-trader.server :as server]
   [copy-trader.websocket.client :as ws-client]))

(defn halt
  []
  (when (:is-running? @core/state)
    ;; FIXME
    (comment "disconnect websocket clients")
    (scheduler/halt!)
    (server/halt!)
    (reset! core/state {:is-running? false}))
  :halted)

(defn go
  []
  (when-not (:is-running? @core/state)
    (traders/load-traders!)
    (scheduler/start!)
    (server/start!)
    (jobs/schedule-jobs!)
    (swap! core/state assoc :is-running? true)
    ;; FIXME
    (comment "connect websocket clients"))
  :running)
