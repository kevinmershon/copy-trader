(ns copy-trader.scheduling.scheduler
  (:require
   [clojurewerkz.quartzite.scheduler :as scheduler]
   [clojurewerkz.quartzite.schedule.cron :as cron]
   [clojurewerkz.quartzite.triggers :as triggers]
   [copy-trader.core :as core]))

(defonce ^:private schedulers-atom (atom {}))

(defn is-scheduler-running?
  []
  (:is-scheduler-running? @core/state))

(defn start!
  []
  (when (empty? @schedulers-atom)
    (reset! schedulers-atom
            {:default (scheduler/initialize)
             :fetch   (scheduler/initialize)
             :trader  (scheduler/initialize)}))

  (when-not (is-scheduler-running?)
    (do
      (scheduler/start (:default @schedulers-atom))
      (scheduler/start (:fetch @schedulers-atom))
      (scheduler/start (:trader @schedulers-atom))
      (swap! core/state assoc :is-scheduler-running? true)
      :started)))

(defn halt!
  []
  (let [schedulers @schedulers-atom]
    (when (and (not-empty schedulers)
               (is-scheduler-running?))
      (doseq [sched-key (keys schedulers)]
        (scheduler/shutdown (get schedulers sched-key))
        (swap! core/state assoc :is-scheduler-running? false))
      (reset! schedulers-atom {})
      :halted)))

(defn schedule-cron-job!
  ([target-scheduler job cron-schedule-string]
   {:pre [(keyword? target-scheduler) (string? cron-schedule-string)]}
   (schedule-cron-job! target-scheduler job cron-schedule-string false))
  ([target-scheduler job cron-schedule-string skip-misfires?]
   {:pre [(string? cron-schedule-string) (boolean? skip-misfires?)]}
   (let [job-schedule (cron/schedule
                       (cron/cron-schedule cron-schedule-string)
                       (cron/in-time-zone (java.util.TimeZone/getTimeZone "UTC")))
         job-schedule (if skip-misfires?
                        (cron/with-misfire-handling-instruction-do-nothing job-schedule)
                        job-schedule)
         job-trigger  (triggers/build
                       (triggers/start-now)
                       (triggers/with-schedule job-schedule))]
     (scheduler/schedule
      (get @schedulers-atom target-scheduler)
      job
      job-trigger))))
