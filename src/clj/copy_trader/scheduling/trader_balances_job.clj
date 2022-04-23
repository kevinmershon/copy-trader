(ns copy-trader.scheduling.trader-balances-job
  (:require
   [clojure.tools.logging :as log]
   [clojurewerkz.quartzite.jobs :as jobs :refer [defjob]]
   [copy-trader.cache :refer [with-cache]]
   [copy-trader.exchange.traders :refer [all-traders]]
   [copy-trader.exchange.trader :refer [;;cache-orders! cache-positions!
                                        with-balance with-orders with-positions]]))

(defn- update-trader-balances!
  [trader-state]
  (swap! trader-state (fn [trader-map]
                        (->> trader-map
                             with-balance
                             with-positions
                             with-orders
                             with-cache))))

(defn- do-trader-balances-job*
  [_job-context]
  (doseq [trader-state (all-traders)]
    (update-trader-balances! trader-state)))

(defjob trader-balances-job
  [job-context]
  (try
    (do-trader-balances-job* job-context)
    (catch Throwable t
      (log/error t))))

(defn make-trader-balances-job
  []
  (jobs/build
   (jobs/of-type trader-balances-job)
   (jobs/using-job-data {})))
