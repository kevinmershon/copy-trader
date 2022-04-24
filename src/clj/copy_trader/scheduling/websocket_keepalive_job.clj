(ns copy-trader.scheduling.websocket-keepalive-job
  (:require
   [clojure.tools.logging :as log]
   [clojurewerkz.quartzite.jobs :as jobs :refer [defjob]]
   [copy-trader.websocket.client :as ws-client]))

(defn- do-websocket-keepalive-job*
  [_job-context]
  (ws-client/keepalive-clients!))

(defjob websocket-keepalive-job
  [job-context]
  (try
    (do-websocket-keepalive-job* job-context)
    (catch Throwable t
      (log/error t))))

(defn make-websocket-keepalive-job
  []
  (jobs/build
   (jobs/of-type websocket-keepalive-job)
   (jobs/using-job-data {})))
