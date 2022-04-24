(ns copy-trader.scheduling.jobs
  (:require
   [copy-trader.scheduling.scheduler :as scheduler]
   [copy-trader.scheduling.trader-balances-job :as trader-balances-job]
   [copy-trader.scheduling.websocket-keepalive-job :as websocket-keepalive-job]))

(defn schedule-jobs!
  []
  ;; TODO -- add more jobs here as needed
  (scheduler/schedule-cron-job!
   :fetch
   (websocket-keepalive-job/make-websocket-keepalive-job)
   ;; seconds minutes hours dom month dow
   ;; every 10 seconds
   "*/10 * * ? * *")
  (scheduler/schedule-cron-job!
   :fetch
   (trader-balances-job/make-trader-balances-job)
   ;; seconds minutes hours dom month dow
   ;; every 20 seconds
   "10,30,50 * * ? * *"))
